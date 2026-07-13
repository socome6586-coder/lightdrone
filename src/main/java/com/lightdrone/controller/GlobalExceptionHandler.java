package com.lightdrone.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /** 잘못된 요청 - 컨트롤러 내에서 발생한 IllegalArgumentException */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgument(HttpServletRequest req,
                                        IllegalArgumentException e,
                                        Model model) {
        log.warn("Bad Request [{}]: {}", req.getRequestURI(), e.getMessage());
        model.addAttribute("errorMessage", e.getMessage());
        return "error/400";
    }

    /**
     * 존재하지 않는 경로/정적 리소스 요청 → 404 (500 아님).
     * 매핑되지 않은 URL 이나 없는 이미지 요청이 500 으로 처리되던 문제를 바로잡습니다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResource(HttpServletRequest req, NoResourceFoundException e) {
        log.warn("Not Found [{}]: {}", req.getRequestURI(), e.getMessage());
        return "error/404";
    }

    /**
     * 업로드 파일 용량 초과 — 멀티파트 파싱은 컨트롤러 진입 전에 일어나므로
     * 각 컨트롤러의 try/catch 가 잡지 못한다. 여기서 친절한 400 으로 안내한다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleMaxUploadSize(HttpServletRequest req,
                                      MaxUploadSizeExceededException e,
                                      Model model) {
        log.warn("Upload too large [{}]: {}", req.getRequestURI(), e.getMessage());
        model.addAttribute("errorMessage",
                "업로드할 수 있는 최대 파일 크기(10MB)를 초과했습니다. 파일 크기를 줄여 다시 시도해 주세요.");
        return "error/400";
    }

    /** 미처리 예외 — 500 에러 원인을 로그로 기록 (Spring Security 예외는 제외) */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneral(HttpServletRequest req, Exception e, Model model) {
        // Spring Security 접근 거부 예외는 Security 필터가 처리하도록 재전파
        if (e instanceof AccessDeniedException) {
            throw (AccessDeniedException) e;
        }
        log.error("서버 오류 [{}]: {}", req.getRequestURI(), e.getMessage(), e);
        return "error/500";
    }
}
