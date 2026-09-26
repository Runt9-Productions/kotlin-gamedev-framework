package com.runt9.kgdf.api.result

import com.runt9.kgdf.api.observe.ApiScreen
import com.runt9.kgdf.api.observe.renderHop
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

/**
 * Every JSON response.
 *
 * [currentScreen] rides on all of them, not only on `GET /currentScreen`, because an action can change which
 * screen is up and the caller cannot predict which: one action can land on any of several screens,
 * depending on state the caller has not read.
 */
@Serializable
class ApiResult<T>(val currentScreen: ApiScreen, val data: T)

/** For an endpoint whose entire answer is [ApiResult.currentScreen]. Serializes as `{}`. */
@Serializable
object NoData

/** Hops to read [ApiScreen.current], which walks Scene2D, so never call this from inside another hop. */
suspend inline fun <reified T> ApplicationCall.respondApi(data: T) = respond(ApiResult(renderHop { ApiScreen.current }, data))

/**
 * Deliberately not `inline` with a `reified` type, unlike [respondApi] directly above it.
 *
 * It takes no type parameter, so `T` is already concretely [NoData] where [respondApi] inlines, and the
 * `typeInfo` ContentNegotiation resolves its serializer from stays complete. Making this generic is what would
 * erase it, and that failure lands at request time rather than compile time.
 */
suspend fun ApplicationCall.respondNoData() = respondApi(NoData)
