/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Principal;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import io.honeycomb.Event;
import io.honeycomb.HoneyException;
import io.honeycomb.LibHoney;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class HoneycombFilter implements Filter {

    private final Log log = LogFactory.getLog(HoneycombFilter.class);

    private volatile LibHoney libhoney;

    public void init(FilterConfig filterConfig) throws ServletException {
        libhoney = new LibHoney.Builder()
                .writeKey(filterConfig.getInitParameter("honeycomb.writeKey"))
                .dataSet(filterConfig.getInitParameter("honeycomb.dataSet"))
                .build();
        try {
            libhoney.addField("server", InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            log.error(e.getMessage(), e);
        }
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest req = (HttpServletRequest) request;
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long responseTimeNanos = System.nanoTime() - start;
            Event event = libhoney.newEvent();
            event.addField("method", req.getMethod());
            event.addField("path", req.getRequestURI());
            event.addField("query", req.getQueryString());
            Principal principal = req.getUserPrincipal();
            if (principal != null) {
                event.addField("user", principal.getName());
            }
            event.addField("host", req.getRemoteHost());
            event.addField("responseTimeNanos", responseTimeNanos);
            try {
                event.send();
            } catch (HoneyException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    public void destroy() {
        libhoney.close();
    }
}
