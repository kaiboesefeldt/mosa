package straightway.mosa.defense

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.routingRoot
import io.ktor.util.AttributeKey
import io.ktor.server.routing.path as routePath

object RouteTagResolver {
    private val RouteTemplatesKey = AttributeKey<List<RouteTemplate>>("MosaRouteTemplates")

    fun resolveRouteTag(call: ApplicationCall): String =
        resolveRoutingPath(call.application, call.request.path())?.takeIf { it.isNotBlank() } ?: "UNMATCHED"

    private fun resolveRoutingPath(
        application: Application,
        requestPath: String,
    ): String? {
        val candidates = cachedRouteTemplates(application)
        return candidates
            .asSequence()
            .mapNotNull { template -> scoreRouteMatch(template, requestPath)?.let { score -> template.path to score } }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun cachedRouteTemplates(application: Application): List<RouteTemplate> {
        application.attributes.getOrNull(RouteTemplatesKey)?.let { return it }
        val computed = collectRouteTemplates(application.routingRoot)
        runCatching { application.attributes.put(RouteTemplatesKey, computed) }
        return application.attributes.getOrNull(RouteTemplatesKey)?.takeUnless { it.isEmpty() } ?: computed
    }

    private fun collectRouteTemplates(root: RoutingNode): List<RouteTemplate> {
        val templates = ArrayList<RouteTemplate>()
        val nodes = ArrayDeque<RoutingNode>()
        nodes.add(root)
        while (nodes.isNotEmpty()) {
            val node = nodes.removeFirst()
            val routePath = node.routePath
            if (routePath.isNotBlank() && routePath != "/") {
                templates.add(RouteTemplate(routePath, toSegments(routePath)))
            }
            nodes.addAll(node.children)
        }
        return templates.toList()
    }

    private fun scoreRouteMatch(
        template: RouteTemplate,
        requestPath: String,
    ): Int? {
        val routeSegments = template.segments
        val requestSegments = requestPath.trim('/').split('/').filter { it.isNotEmpty() }
        if (routeSegments.size != requestSegments.size) return null

        var score = 0
        for (index in routeSegments.indices) {
            val routeSegment = routeSegments[index]
            val requestSegment = requestSegments[index]
            if (isPathParameter(routeSegment)) {
                score += 1
            } else {
                if (routeSegment != requestSegment) return null
                score += 2
            }
        }
        return score
    }

    private fun isPathParameter(segment: String): Boolean = segment.startsWith("{") && segment.endsWith("}") && segment.length > 2

    private fun toSegments(path: String): List<String> = path.trim('/').split('/').filter { it.isNotEmpty() }

    private data class RouteTemplate(
        val path: String,
        val segments: List<String>,
    )
}
