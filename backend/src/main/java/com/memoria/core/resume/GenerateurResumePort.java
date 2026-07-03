package com.memoria.core.resume;

// Detail d'infrastructure remplacable (Azure OpenAI aujourd'hui, un autre fournisseur demain).
public interface GenerateurResumePort {

    ResumeGenere genererResume(String transcriptComplet);
}
