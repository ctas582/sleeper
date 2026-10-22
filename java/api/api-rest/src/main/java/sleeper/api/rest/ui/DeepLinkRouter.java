/*
 * Copyright 2022-2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sleeper.api.rest.ui;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Serves the UI.
 */
@Singleton
public class DeepLinkRouter {

    private static final String UI_PAGE = "/index.html";
    private static final List<String> NON_UI_ROUTE_PREFIXES = List.of("/api", "/q");

    /**
     * Registers the rewrite, before the security rules are applied.
     *
     * @param filters the filters the server applies to a request
     */
    public void register(@Observes Filters filters) {
        filters.register(this::rewriteToUI, Integer.MAX_VALUE);
    }

    private void rewriteToUI(RoutingContext context) {
        if (isUIPage(context)) {
            context.reroute(UI_PAGE);
        } else {
            context.next();
        }
    }

    private static boolean isUIPage(RoutingContext context) {
        if (!HttpMethod.GET.equals(context.request().method())) {
            return false;
        }
        String path = context.normalizedPath();
        if (UI_PAGE.equals(path) || "/".equals(path) || path.startsWith("/assets/")) {
            return false;
        }
        return NON_UI_ROUTE_PREFIXES.stream()
                .noneMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

}
