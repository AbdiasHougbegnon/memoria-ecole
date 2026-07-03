package com.memoria.core.web;

import com.memoria.core.audio.SessionNonActiveException;
import com.memoria.core.resume.ResumeNotFoundException;
import com.memoria.core.session.SessionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GestionnaireExceptionsApi {

    @ExceptionHandler(SessionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void gererSessionIntrouvable() {
    }

    @ExceptionHandler(SessionNonActiveException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void gererSessionNonActive() {
    }

    @ExceptionHandler(ResumeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void gererResumeIntrouvable() {
    }
}
