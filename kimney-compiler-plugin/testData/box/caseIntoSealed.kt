import io.github.matthewjones372.kimney.transformInto

sealed interface Expr {
    data class Num(val value: Int) : Expr
    data class Add(val left: Expr, val right: Expr) : Expr
}
sealed interface ExprDto {
    data class Num(val value: Int) : ExprDto
    data class Add(val left: ExprDto, val right: ExprDto) : ExprDto
}

fun box(): String {
    val add = Expr.Add(Expr.Num(1), Expr.Num(2))
    val dto: ExprDto = add.transformInto<ExprDto>()
    return if (dto == ExprDto.Add(ExprDto.Num(1), ExprDto.Num(2))) "OK" else "Fail: $dto"
}
