package com.speakbetter.speaking.provider;

import com.speakbetter.speaking.domain.DrillEvaluation;
import com.speakbetter.speaking.domain.DrillItem;
import com.speakbetter.speaking.domain.SpeakingSession;

import java.util.List;

public interface DrillEngine {

    String id();

    GeneratedDrill generate(SpeakingSession session);

    DrillEvaluation evaluateOne(DrillItem item, String studentEnglish, String level);

    List<DrillEvaluation> evaluateFullRetelling(
            List<DrillItem> items,
            String fullEnglishRetelling,
            String level
    );

    record GeneratedDrill(String title, List<DrillItem> items, String instructions) {}
}
