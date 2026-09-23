package io.github.matthewjones372.kimney.compiler.fir

import io.github.matthewjones372.kimney.compiler.TRANSFORM_INTO
import io.github.matthewjones372.kimney.compiler.internalError
import io.github.matthewjones372.kimney.compiler.isControlFlow
import io.github.matthewjones372.kimney.derive.Derived
import io.github.matthewjones372.kimney.derive.derive
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeErrorType
import org.jetbrains.kotlin.fir.types.resolvedType

/** Reports every reason a `transformInto` call cannot be derived, on the call itself. */
object KimneyCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val callee = expression.calleeReference.symbol as? FirNamedFunctionSymbol ?: return
        if (callee.callableId != TRANSFORM_INTO) return
        val source = expression.extensionReceiver?.resolvedType ?: return
        val target = expression.resolvedType
        // The compiler has already reported whatever left a type unresolved.
        if (source is ConeErrorType || target is ConeErrorType) return

        try {
            val model = FirTypeModel(context.session)
            val derived = derive(model, source, target)
            if (derived is Derived.Failed) {
                val message = derived.message(model.render(source), model.render(target))
                reporter.reportOn(expression.source, KimneyErrors.KIMNEY_CANNOT_TRANSFORM, message)
            }
        } catch (e: Exception) {
            if (e.isControlFlow()) throw e
            reporter.reportOn(expression.source, KimneyErrors.KIMNEY_INTERNAL_ERROR, internalError(e))
        }
    }
}
