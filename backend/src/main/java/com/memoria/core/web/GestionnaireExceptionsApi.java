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

    @ExceptionHandler(com.memoria.core.resume.AucuneTranscriptionDisponibleException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void gererAucuneTranscriptionDisponibleResume() {
    }

    @ExceptionHandler(com.memoria.entreprise.compterendu.CompteRenduNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void gererCompteRenduIntrouvable() {
    }

    @ExceptionHandler(com.memoria.entreprise.compterendu.AucuneTranscriptionDisponibleException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void gererAucuneTranscriptionDisponibleCompteRendu() {
    }

    @ExceptionHandler(com.memoria.entreprise.engagement.EngagementNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void gererEngagementIntrouvable() {
    }

    @ExceptionHandler(com.memoria.entreprise.engagement.TransitionEngagementInvalideException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void gererTransitionEngagementInvalide() {
    }

    @ExceptionHandler(com.memoria.core.auth.EmailDejaUtiliseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void gererEmailDejaUtilise() {
    }

    @ExceptionHandler(com.memoria.core.auth.IdentifiantsInvalidesException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void gererIdentifiantsInvalides() {
    }

    @ExceptionHandler(com.memoria.ecole.resumecours.ResumeCoursNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void gererResumeCoursIntrouvable() {
    }

    @ExceptionHandler(com.memoria.ecole.resumecours.AucuneTranscriptionDisponibleException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void gererAucuneTranscriptionDisponibleResumeCours() {
    }
}
