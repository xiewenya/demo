package com.example.demo.filter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * @author bresai
 */

public class PathInfoRequestWrapper extends HttpServletRequestWrapper {
    private static final String SOURCE_ROOT = "/src";

    private static final String FILE_ROOT = "/file";

    private HttpServletRequest request;

    public PathInfoRequestWrapper(HttpServletRequest request) {
        super(request);
        this.request = request;
    }

    @Override
    public String getPathInfo() {
        String path = this.getRequestURI();

        if (path.startsWith(FILE_ROOT)){
            return path.substring(FILE_ROOT.length());
        }

        if (path.startsWith(SOURCE_ROOT)){
            return path.substring(SOURCE_ROOT.length());
        }

        return path;
    }
}
