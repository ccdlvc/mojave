/*
 * Copyright (C) 2011-2013 Mojavemvc.org
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
package org.mojavemvc.tests.controllers;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mojavemvc.annotations.Action;
import org.mojavemvc.annotations.AfterConstruct;
import org.mojavemvc.annotations.BeforeAction;
import org.mojavemvc.annotations.DefaultAction;
import org.mojavemvc.annotations.DefaultController;
import org.mojavemvc.annotations.Param;
import org.mojavemvc.annotations.StatelessController;
import org.mojavemvc.aop.RequestContext;
import org.mojavemvc.initialization.AppProperties;
import org.mojavemvc.tests.services.SomeProvidedService;
import org.mojavemvc.tests.services.SomeService;
import org.mojavemvc.views.JSP;
import org.mojavemvc.views.PlainText;
import org.mojavemvc.views.Response;
import org.mojavemvc.views.View;

import com.google.inject.Inject;

/**
 * 
 * @author Luis Antunes
 */
@DefaultController
@StatelessController("index")
public class IndexController {

    @Inject
    private HttpServletRequest request;

    @Inject
    private SomeService someService;
    
    @Inject
    private SomeProvidedService someProvidedService;

    @Inject
    private IInjectableController injectedController;

    /**
     * No-arg constructor.
     */
    public IndexController() {
        super();
    }

    private String initVal;

    @AfterConstruct
    public void init() {
        initVal = "init-called";
    }

    private String reqCtxCntrl;
    private String reqCtxActn;

    @BeforeAction
    public void doSomethingBefore(RequestContext ctx) {

        reqCtxCntrl = ctx.getController();
        reqCtxActn = ctx.getAction();
    }

    @Action("test-init")
    public View testInitAction() {
        return new JSP("param").withAttribute("var", initVal);
    }

    @DefaultAction
    public View defaultActn() {
        return new JSP("param").withAttribute("var", reqCtxCntrl + " " + reqCtxActn);
    }

    @Action("test")
    public View testAction() {
        return new JSP("test");
    }

    @Action("with-param")
    public View withParamAction() {
        
        return new JSP("param", new String[] { "var" }, new Object[] { getParameter("var") });
    }

    @Action("another-param")
    public View anotherParamAction() {
        
        return new JSP("param").withAttribute("var", getParameter("var"));
    }

    @Action("some-service")
    public View someServiceAction() {
        
        String answer = someService.answerRequest(getParameter("var"));

        return new JSP("some-service").withAttribute("var", answer);
    }
    
    @Action("some-provided-service")
    public View someProvidedServiceAction(@Param("var") String var) {
        
        String processed = someProvidedService.processRequest(var);
        
        return new JSP("param").withAttribute("var", processed);
    }

    @Action("test-annotation")
    public View doAnnotationTest() {
        
        return new JSP("param").withAttribute("var", getParameter("var"));
    }

    @Action("param-annotation-string")
    public View paramAnnotationTest(@Param("p1") String p1) {

        return new JSP("params").withAttribute("p1", p1 == null ? "null" : p1);
    }

    @Action("param-annotation-string2")
    public View paramAnnotationTest2(@Param("p1") String p1, @Param("p2") String p2) {

        return new JSP("params2").withAttribute("p1", p1).withAttribute("p2", p2);
    }

    @Action("param-annotation-int")
    public View paramAnnotationTest3(@Param("p1") int p1) {

        return new JSP("params").withAttribute("p1", p1);
    }

    @Action("param-annotation-double")
    public View paramAnnotationTest4(@Param("p1") double p1) {

        return new JSP("params").withAttribute("p1", p1);
    }
    
    @Action("param-annotation-bigdecimal")
    public View paramAnnotationTestBigDecimal(@Param("p1") BigDecimal p1) {

        return new JSP("params").withAttribute("p1", p1);
    }

    @Action("param-annotation-date")
    public View paramAnnotationTest5(@Param("p1") Date p1) {

        return new JSP("params").withAttribute("p1", p1 == null ? "null" : p1.toString());
    }

    @Action("param-annotation-all")
    public View paramAnnotationTest6(@Param("p1") Date p1, @Param("p2") String p2, @Param("p3") int p3,
            @Param("p4") double p4) {

        return new JSP("params3").withAttribute("p1", p1.toString()).withAttribute("p2", p2)
                .withAttribute("p3", p3).withAttribute("p4", p4);
    }

    @Action("param-annotation-bool")
    public View paramAnnotationTest7(@Param("p1") boolean p1) {

        return new JSP("params").withAttribute("p1", p1);
    }

    @Action("param-annotation-ints")
    public View paramAnnotationTest8(@Param("p1") int[] p1) {

        return new JSP("params3").withAttribute("p1", p1[0]).withAttribute("p2", p1[1])
                .withAttribute("p3", p1[2]).withAttribute("p4", p1[3]);
    }

    @Action("param-annotation-strings")
    public View paramAnnotationTest9(@Param("p1") String[] p1) {

        return new JSP("params3").withAttribute("p1", p1[0]).withAttribute("p2", p1[1])
                .withAttribute("p3", p1[2]).withAttribute("p4", p1[3]);
    }

    @Action("param-annotation-doubles")
    public View paramAnnotationTest10(@Param("p1") double[] p1) {

        return new JSP("params3").withAttribute("p1", p1[0]).withAttribute("p2", p1[1])
                .withAttribute("p3", p1[2]).withAttribute("p4", p1[3]);
    }
    
    @Action("param-annotation-bigdecimals")
    public View paramAnnotationTestBigDecimals(@Param("p1") BigDecimal[] p1) {

        return new JSP("params3").withAttribute("p1", p1[0]).withAttribute("p2", p1[1])
                .withAttribute("p3", p1[2]).withAttribute("p4", p1[3]);
    }

    @Action("param-annotation-dates")
    public View paramAnnotationTest11(@Param("p1") Date[] p1) {

        return new JSP("params3").withAttribute("p1", p1[0].toString()).withAttribute("p2", p1[1].toString())
                .withAttribute("p3", p1[2].toString()).withAttribute("p4", p1[3].toString());
    }

    @Action("param-annotation-bools")
    public View paramAnnotationTest12(@Param("p1") boolean[] p1) {

        return new JSP("params3").withAttribute("p1", p1[0]).withAttribute("p2", p1[1])
                .withAttribute("p3", p1[2]).withAttribute("p4", p1[3]);
    }

    @Action("injected")
    public View injectedAction() {

        String var = injectedController.process("index");
        return new JSP("param").withAttribute("var", var);
    }

    @Action("plain-text")
    public PlainText getText() {
        return new PlainText("hello");
    }
    
    @Action("status-ok")
    public View statusOK() {
        return new Response.OK()
            .withContent("it's ok")
            .withContentType("text/plain")
            .withLanguage("English");
    }
    
    @Action("include")
    public View include() {
        return new View() {
            @Override
            public void render(HttpServletRequest request, 
                    HttpServletResponse response, 
                    AppProperties properties) throws ServletException,
                    IOException {
                
                String jspPath = (String)properties.getProperty(JSP.PATH_PROPERTY);
                RequestDispatcher dispatcher = 
                        request.getRequestDispatcher(jspPath + "include.jsp");
                dispatcher.include(request, response);
            }
        };
    }
    
    public String getParameter(String key) {

        return request.getParameter(key);
    }

    public boolean parameterIsEmpty(String key) {

        if (getParameter(key) == null || getParameter(key).trim().length() == 0) {
            return true;
        }
        return false;
    }
}