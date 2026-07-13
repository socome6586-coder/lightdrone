package com.lightdrone.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Integer status  = (Integer)   request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String  message = (String)    request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        String  uri     = (String)    request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Throwable ex    = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        if (ex != null) {
            log.error("=== SERVER ERROR {} at [{}] ===", status, uri, ex);
        } else {
            log.warn("Error {} at [{}]: {}", status, uri, message);
        }

        if (status == null) return "error/500";

        model.addAttribute("errorMessage", message);

        return switch (status) {
            case 400 -> "error/400";
            case 403 -> "error/403";
            case 404 -> "error/404";
            default  -> "error/500";
        };
    }
}
