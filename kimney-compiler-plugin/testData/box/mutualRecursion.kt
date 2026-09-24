import io.github.matthewjones372.kimney.transformInto

data class Folder(val name: String, val files: List<File>, val folders: List<Folder>)
data class File(val name: String, val attachment: Folder?)
data class FolderDto(val name: String, val files: List<FileDto>, val folders: List<FolderDto>)
data class FileDto(val name: String, val attachment: FolderDto?)

fun box(): String {
    val inner = Folder("inner", listOf(File("x", null)), emptyList())
    val root = Folder("root", listOf(File("readme", inner)), listOf(inner))
    val dto = root.transformInto<FolderDto>()
    val innerDto = FolderDto("inner", listOf(FileDto("x", null)), emptyList())
    return if (dto == FolderDto("root", listOf(FileDto("readme", innerDto)), listOf(innerDto))) "OK" else "Fail: $dto"
}
