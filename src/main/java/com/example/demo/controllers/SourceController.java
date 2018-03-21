package com.example.demo.controllers;

import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.HistoryException;
import org.opensolaris.opengrok.search.DirectoryEntry;
import org.opensolaris.opengrok.search.DirectoryExtraReader;
import org.opensolaris.opengrok.search.FileExtra;
import org.opensolaris.opengrok.util.FileExtraZipper;
import org.opensolaris.opengrok.web.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.List;
import java.util.Set;

/**
 * @author bresai
 */

@Controller
@RequestMapping("/src")
public class SourceController {

    @RequestMapping(path = "/**")
    public String getSource(Model model,
                            HttpServletRequest request,
                            HttpServletResponse response) throws IOException, HistoryException {
        PageConfig cfg = PageConfig.get(request);
        cfg.checkSourceRootExistence();

        StringWriter out = new StringWriter();
        File resourceFile = cfg.getResourceFile();

        String path = cfg.getPath();
        String basename = resourceFile.getName();
        String rawPath = request.getContextPath() + Prefix.DOWNLOAD_P + path;
        Reader r = null;

        if (cfg.isDir()) {
            // valid resource is requested
            // mast.jsp assures, that resourceFile is valid and not /
            // see cfg.resourceNotAvailable()
            Project activeProject = Project.getProject(resourceFile);
            String cookieValue = cfg.getRequestedProjectsAsString();
            if (activeProject != null) {
                Set<String> projects = cfg.getRequestedProjects();
                if (!projects.contains(activeProject.getName())) {
                    projects.add(activeProject.getName());
                    // update cookie
                    cookieValue = cookieValue.length() == 0
                            ? activeProject.getName()
                            : activeProject.getName() + ',' + cookieValue;
                    Cookie cookie = new Cookie(PageConfig.OPEN_GROK_PROJECT, URLEncoder.encode(cookieValue, "utf-8"));
                    // TODO hmmm, projects.jspf doesn't set a path
                    cookie.setPath(request.getContextPath() + '/');
                    response.addCookie(cookie);
                }
            }
            // requesting a directory listing
            DirectoryListing dl = new DirectoryListing(cfg.getEftarReader());
            List<String> files = cfg.getResourceFileList();
            if (!files.isEmpty()) {
                List<FileExtra> extras = null;
                if (activeProject != null) {
                    SearchHelper searchHelper = cfg.prepareInternalSearch();
                    // N.b. searchHelper.destroy() is called via
                    // WebappListener.requestDestroyed() on presence of the
                    // following REQUEST_ATTR.
                    request.setAttribute(SearchHelper.REQUEST_ATTR, searchHelper);
                    searchHelper.prepareExec(activeProject);

                    if (searchHelper.searcher != null) {
                        DirectoryExtraReader extraReader =
                                new DirectoryExtraReader();
                        extras = extraReader.search(searchHelper.searcher, path);
                    }
                }

                FileExtraZipper zipper = new FileExtraZipper();
                List<DirectoryEntry> entries = zipper.zip(resourceFile, files,
                        extras);

                List<String> readMes = dl.extraListTo(
                        Util.URIEncodePath(request.getContextPath()),
                        resourceFile, out, path, entries);
                File[] catfiles = cfg.findDataFiles(readMes);
                for (int i = 0; i < catfiles.length; i++) {
                    if (catfiles[i] == null) {
                        continue;
                    }
                }

                model.addAttribute("contextPath", cfg.getPath());
                model.addAttribute("file", out.toString());
                return "dir";
            }
        } else {
            File xrefFile = cfg.findDataFile();
            Util.dump(out, xrefFile, xrefFile.getName().endsWith(".gz"));
            model.addAttribute("file", out.toString());
            return "file";
        }

        return "dir";
    }
}
