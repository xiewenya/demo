package com.example.demo.filter;

import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component
public class PathInfoFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        PathInfoRequestWrapper pathInfoRequest = new PathInfoRequestWrapper((HttpServletRequest) servletRequest);
        filterChain.doFilter(pathInfoRequest, servletResponse);
    }

    @Override
    public void destroy() {

    }
}
