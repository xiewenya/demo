package com.example.demo.controllers;

import com.example.demo.security.NoExitSecurityManager;
import com.example.demo.services.AstIndexer;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author bresai
 */

@RestController
public class ToolController {

    @RequestMapping("/indexer")
    public String indexer() {
        //prevent opengrok exit from error
        if (!(System.getSecurityManager() instanceof NoExitSecurityManager)) {
            SecurityManager securityManager = System.getSecurityManager();
            System.setSecurityManager(new NoExitSecurityManager(securityManager));
        }

        try {
            AstIndexer.getAstIndexer().indexer("/Users/bresai/opengrok/data/", "/Users/bresai/opengrok/data/", "/Users/bresai/opengrok/etc/configuration.xml");
        } catch (Exception e) {
            System.out.println("indexer exit with error");
        }

        return "ok";
    }
}