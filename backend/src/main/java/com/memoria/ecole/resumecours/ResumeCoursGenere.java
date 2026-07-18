package com.memoria.ecole.resumecours;

import java.util.List;

public record ResumeCoursGenere(String synthese, List<NotionExtraite> notions, List<String> pointsARevoir) {
}
