import io.github.matthewjones372.kimney.transformInto

sealed interface Expr {
    data class Num(val value: Int) : Expr
    data class Add(val left: Expr, val right: Expr) : Expr
    data class Neg(val of: Expr) : Expr
}
sealed interface ExprDto {
    data class Num(val value: Int) : ExprDto
    data class Add(val left: ExprDto, val right: ExprDto) : ExprDto
    data class Neg(val of: ExprDto) : ExprDto
}

fun box(): String {
    val expr: Expr = Expr.Add(Expr.Num(1), Expr.Neg(Expr.Add(Expr.Num(2), Expr.Num(3))))
    val dto = expr.transformInto<ExprDto>()
    val expected = ExprDto.Add(ExprDto.Num(1), ExprDto.Neg(ExprDto.Add(ExprDto.Num(2), ExprDto.Num(3))))
    return if (dto == expected) "OK" else "Fail: $dto"
}
