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

    @ExceptionHandler(com.memoria.core.auth.DomaineEmailNonAutoriseException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public void gererDomaineEmailNonAutorise() {
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

    @ExceptionHandler(com.memoria.core.couloir.CouloirNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void gererCouloirIntrouvable() {
    }

    @ExceptionHandler(com.memoria.core.couloir.PasMembreDuCouloirException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public void gererPasMembreDuCouloir() {
    }

    @ExceptionHandler(com.memoria.core.couloir.PasProprietaireDuCouloirException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public void gererPasProprietaireDuCouloir() {
    }

    @ExceptionHandler(com.memoria.core.couloir.ProprietaireNePeutPasSeRetirerException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void gererProprietaireNePeutPasSeRetirer() {
    }

    @ExceptionHandler(com.memoria.core.couloir.NouveauProprietaireDoitEtreMembreException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void gererNouveauProprietaireDoitEtreMembre() {
    }

    @ExceptionHandler(com.memoria.core.auth.UtilisateurNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void gererUtilisateurIntrouvable() {
    }

    @ExceptionHandler(com.memoria.core.locuteur.ConsentementRequisException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void gererConsentementRequis() {
    }

    @ExceptionHandler(com.memoria.core.locuteur.AudioEnrollementInsuffisantException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void gererAudioEnrollementInsuffisant() {
    }

    @ExceptionHandler(com.memoria.ecole.matiere.MatiereNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void gererMatiereIntrouvable() {
    }

    @ExceptionHandler(com.memoria.ecole.notion.NotionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void gererNotionIntrouvable() {
    }

    @ExceptionHandler(com.memoria.ecole.seance.SeanceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void gererSeanceIntrouvable() {
    }

    @ExceptionHandler(com.memoria.ecole.tuteurvocal.SeanceTutoratNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void gererSeanceTutoratIntrouvable() {
    }

    @ExceptionHandler(com.memoria.ecole.tuteurvocal.TourDialogueTutoratNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void gererTourDialogueTutoratIntrouvable() {
    }

    @ExceptionHandler(com.memoria.ecole.tuteurvocal.AccesTutoratRefuseException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public void gererAccesTutoratRefuse() {
    }

    @ExceptionHandler(com.memoria.ecole.tuteurvocal.SeanceTutoratNonActiveException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void gererSeanceTutoratNonActive() {
    }

    @ExceptionHandler(com.memoria.ecole.tuteurvocal.TraitementTourTutoratException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public void gererTraitementTourTutoratEnEchec() {
    }
}
