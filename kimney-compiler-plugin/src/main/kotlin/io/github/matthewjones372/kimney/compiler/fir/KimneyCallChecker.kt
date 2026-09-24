package io.github.matthewjones372.kimney.compiler.fir

import io.github.matthewjones372.kimney.compiler.INTO
import io.github.matthewjones372.kimney.compiler.KimneyErrors
import io.github.matthewjones372.kimney.compiler.OVERRIDES
import io.github.matthewjones372.kimney.compiler.TERMINALS
import io.github.matthewjones372.kimney.compiler.TRANSFORM
import io.github.matthewjones372.kimney.compiler.TRANSFORM_INTO
import io.github.matthewjones372.kimney.compiler.TRANSFORM_INTO_PARTIAL
import io.github.matthewjones372.kimney.compiler.TRANSFORM_PARTIAL
import io.github.matthewjones372.kimney.compiler.guarded
import io.github.matthewjones372.kimney.derive.Derived
import io.github.matthewjones372.kimney.derive.Failure
import io.github.matthewjones372.kimney.derive.Path
import io.github.matthewjones372.kimney.derive.derive
import io.github.matthewjones372.kimney.derive.linksUsed
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeErrorType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.resolvedType

/** Reports every reason a `transformInto` call or an override chain cannot be derived, on the call itself. */
object KimneyCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val id = expression.callableId ?: return
        val report = { message: String ->
            reporter.reportOn(expression.source, KimneyErrors.KIMNEY_INTERNAL_ERROR, message)
        }
        guarded(fallback = {}, report = report) {
            when (id) {
                TRANSFORM_INTO -> expression.extensionReceiver?.resolvedType?.let {
                    derived(expression, it, chain = null)
                }

                TRANSFORM_INTO_PARTIAL -> expression.extensionReceiver?.resolvedType?.let {
                    derived(expression, it, chain = null, partial = true)
                }

                TRANSFORM -> transform(expression)

                TRANSFORM_PARTIAL -> transform(expression, partial = true)

                INTO, in OVERRIDES -> escapes(expression)
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun transform(call: FirFunctionCall, partial: Boolean = false) {
        val chain = readChain(call)
        when {
            chain == null -> call.explicitReceiver?.resolvedType?.let { notStatic(call, it) }

            chain.notAnEntry != null ->
                call.explicitReceiver?.resolvedType?.let { notAnEntry(call, it, chain.notAnEntry) }

            else -> derived(call, chain.source, chain, partial)
        }
    }

    /** A link of the chain whose result is anything but the receiver of the next link has escaped the expression. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun escapes(call: FirFunctionCall) {
        val parent = context.callsOrAssignments.let { it.getOrNull(it.size - 2) } as? FirFunctionCall
        val linked =
            parent?.explicitReceiver === call && (parent.callableId in TERMINALS || parent.callableId in OVERRIDES)
        if (!linked) notStatic(call, call.resolvedType)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun derived(call: FirFunctionCall, source: ConeKotlinType, chain: FirChain?, partial: Boolean = false) {
        // A partial call returns Partial<B>; B is what is derived.
        val target = if (partial) {
            (call.resolvedType as? ConeClassLikeType)?.typeArguments?.singleOrNull() as? ConeKotlinType ?: return
        } else {
            call.resolvedType
        }
        // The compiler has already reported whatever left a type unresolved.
        if (source is ConeErrorType || target is ConeErrorType) return
        val model = FirTypeModel(context.session, context.lookups(call.source))
        val passed = chain?.transformers.orEmpty()
        val transformers = passed + context.contextTransformers(start = chain?.links ?: 0)
        val enums = chain?.enums.orEmpty()
        val sealed = chain?.sealed.orEmpty()
        val overrides = chain?.overrides.orEmpty()
        when (val derived = derive(model, source, target, overrides, transformers, partial, enums, sealed)) {
            is Derived.Failed -> {
                val message = derived.message(model.render(source), model.render(target))
                reporter.reportOn(call.source, KimneyErrors.KIMNEY_CANNOT_TRANSFORM, message)
            }

            is Derived.Planned -> model.unusedLinks(chain, derived.plan.linksUsed()).forEach { unused ->
                reporter.reportOn(chain?.calls?.get(unused.index)?.source, unused.factory, unused.message)
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun notAnEntry(call: FirFunctionCall, into: ConeKotlinType, given: NotAnEntry) {
        val model = FirTypeModel(context.session, context.lookups(call.source))
        val (source, target) = (into as? ConeClassLikeType)?.typeArguments?.map { it as? ConeKotlinType } ?: return
        if (source == null || target == null) return
        val (what, example) = model.writtenOut(given)
        val failure = Failure.NotWrittenOut(Path(model.render(into)), given.method, what, example)
        val message = Derived.Failed(listOf(failure)).message(model.render(source), model.render(target))
        reporter.reportOn(call.source, KimneyErrors.KIMNEY_CANNOT_TRANSFORM, message)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun notStatic(call: FirFunctionCall, into: ConeKotlinType) {
        val model = FirTypeModel(context.session, context.lookups(call.source))
        val (source, target) = (into as? ConeClassLikeType)?.typeArguments?.map { it as? ConeKotlinType } ?: return
        if (source == null || target == null) return
        val failure = Failure.OverrideNotStatic(Path(model.render(into)), model.render(target))
        val message = Derived.Failed(listOf(failure)).message(model.render(source), model.render(target))
        reporter.reportOn(call.source, KimneyErrors.KIMNEY_CANNOT_TRANSFORM, message)
    }
}
