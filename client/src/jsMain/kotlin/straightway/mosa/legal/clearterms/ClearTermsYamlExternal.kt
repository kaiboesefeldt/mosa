@file:JsModule("yaml")
@file:JsNonModule

package straightway.mosa.legal.clearterms

external class Document {
    val errors: Array<YamlError>
    val warnings: Array<YamlError>
    val contents: YamlNode?
}

external fun parseDocument(source: String, options: YamlParseOptions = definedExternally): Document

external fun isMap(node: YamlNode): Boolean

external fun isSeq(node: YamlNode): Boolean

external fun isScalar(node: YamlNode): Boolean

external fun isAlias(node: YamlNode): Boolean

external class YAMLMap : YamlNode {
    val items: Array<YamlPair>
}

external class YAMLSeq : YamlNode {
    val items: Array<YamlNode>
}

@JsName("Scalar")
external class YamlScalar : YamlNode {
    val value: Any?
}

@JsName("Pair")
external class YamlPair {
    val key: YamlNode
    val value: YamlNode
}

external interface YamlNode

external interface YamlError {
    val message: String
}

external interface YamlParseOptions {
    var uniqueKeys: Boolean?
    var merge: Boolean?
    var schema: String?
    var strict: Boolean?
}
