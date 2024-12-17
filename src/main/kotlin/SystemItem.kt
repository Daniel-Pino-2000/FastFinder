// Class representing an item in the system (file or folder)
class SystemItem(
    // Name of the item (file or folder)
    var itemName: String,

    // Path to the item in the system
    var itemPath: String,

    // Boolean flag indicating whether the item is a file (true) or a folder (false)
    var isFile: Boolean
) {

}