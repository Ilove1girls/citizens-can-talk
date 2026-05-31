package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.api.prompt.CitizenPromptProvider;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.SkillLevelView;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.util.RaidTraumaTracker;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation for citizen prompt generation.
 */
public class DefaultCitizenPromptProvider implements CitizenPromptProvider {
    @Override
    public String getBasicCitizenInfoPrompt(@NotNull CitizenPromptView view, boolean firstPerson) {
        StringBuilder prompt = new StringBuilder();
        String name = view.name();
        String citizenType = (view.child() ? "Child" : "Adult") + " " + (view.female() ? "woman" : "man");

        if (firstPerson) {
            prompt.append("# ROLEPLAY AS ").append(name).append("\n\n");
            prompt.append("You: ").append(citizenType);
        } else {
            prompt.append("# CITIZEN INFO ").append(name).append("\n\n");
            prompt.append("Type: ").append(citizenType);
        }

        if (view.jobName() != null) {
            prompt.append(", **").append(view.jobName()).append("**");
            if (view.workBuildingDisplayName() != null) {
                prompt.append(" at ").append(view.workBuildingDisplayName())
                        .append(" (level ").append(view.workBuildingLevel()).append(")");
            }
        } else {
            prompt.append(", **unemployed**");
        }

        var sick = view.sick();
        if (sick) {
            prompt.append(", sick");
        }

        if (view.homeless()) {
            prompt.append(", homeless");
        }

        prompt.append(".\n");
        prompt.append("Colony: **").append(view.colonyName()).append("**");
        if (view.homeBuildingDisplayName() != null && !view.homeless()) {
            prompt.append(" | Home: ").append(view.homeBuildingDisplayName())
                    .append(" (level ").append(view.homeBuildingLevel()).append(")");
        }
        prompt.append("\n\n");
        return prompt.toString();
    }

    private String getGeneralCitizenPrompt(@NotNull CitizenPromptView view, boolean firstPerson) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(getBasicCitizenInfoPrompt(view, firstPerson));

        if (view.skills() != null && !view.skills().isEmpty()) {
            appendCondensedSkills(view.skills(), prompt);
        }

        addRelationships(view, prompt);
        addCurrentState(view, prompt, view.sick());
        addMemory(view, prompt);

        prompt.append("\n## EMOTIONAL PROFILE\n");

        double happiness = view.happiness();

        if (happiness > 8.0) {
            prompt.append("- Generally cheerful and friendly\n");
            prompt.append("- Optimistic about the colony's future\n");
            prompt.append("- Likely to be helpful and engaging\n");
        } else if (happiness > 5.0) {
            prompt.append("- Generally neutral in demeanor\n");
            prompt.append("- Moderately satisfied with life in the colony\n");
            prompt.append("- Can be friendly but has some concerns\n");
        } else if (happiness > 3.0) {
            prompt.append("- Visibly unhappy and somewhat irritable\n");
            prompt.append("- Might complain about colony conditions\n");
            prompt.append("- Less interested in small talk, more focused on needs\n");
        } else {
            prompt.append("- Deeply unhappy and possibly hostile\n");
            prompt.append("- May express concerns directly\n");
            prompt.append("- May be less enthusiastic about casual chat\n");
        }

        if (view.sick()) {
            prompt.append("- Occasionally mentions symptoms or discomfort\n");
        }

        if (!view.blockingInteractionMessages().isEmpty()) {
            prompt.append("You can't do anything else until the following issues are resolved (written in first person):\n");
            for (var message : view.blockingInteractionMessages()) {
                prompt.append("- ").append(message);
            }
        }

        // Personality archetype
        if (view.personality() != null) {
            prompt.append("\n## PERSONALITY\n");
            prompt.append(view.personality().getPromptLines()).append("\n");
        } else if (view.customPersonalityText() != null) {
            prompt.append("\n## PERSONALITY\n");
            prompt.append(view.customPersonalityText()).append("\n");
        }

        return prompt.toString();
    }

    private void addMemory(CitizenPromptView view, StringBuilder prompt) {
        var memories = view.memories();
        if (memories != null) {
            prompt.append("\n## MEMORIES\n");
            prompt.append(memories.toPrompt(view.interestedParties()));
        }
    }

    private void addCurrentState(@NotNull CitizenPromptView view, StringBuilder prompt, boolean sick) {
        prompt.append("\n## CURRENT STATE\n");

        appendDetailedHappinessState(view, prompt);

        double saturation = view.saturation();
        if (saturation <= 1) {
            prompt.append("- Very hungry and weak from lack of food\n");
        } else if (saturation <= 3) {
            prompt.append("- Hungry and thinking about food\n");
        } else if (saturation <= 5) {
            prompt.append("- A bit peckish\n");
        }

        if (view.healthPercent() != null) {
            double healthPercent = view.healthPercent();
            if (healthPercent < 20) {
                prompt.append("- Severely injured, in intense pain\n");
            } else if (healthPercent < 50) {
                prompt.append("- Injured and in pain\n");
            } else if (healthPercent < 75) {
                prompt.append("- Slightly hurt\n");
            } else if (healthPercent == 100) {
                prompt.append("- In perfect health\n");
            }
        }

        if (sick) {
            prompt.append("- Sick and feeling terrible. Needs medical attention\n");
        }

        if (view.homeless()) {
            prompt.append("- Very concerned about not having a home\n");
        }

        if (!view.child() && view.jobName() == null) {
            prompt.append("- Frustrated about not having a job\n");
        }

        final CitizenStatusView status = view.status();
        if (status != null) {
            prompt.append("- Currently: ").append(formatStatus(status)).append("\n");
        }

        // Post-raid trauma
        int traumaDuration = McTalkingConfig.INSTANCE.instance().raidTraumaDurationSeconds;
        if (traumaDuration > 0 && RaidTraumaTracker.isInTrauma(view.colonyId(), traumaDuration)) {
            long sinceMs = RaidTraumaTracker.millisSinceRaid(view.colonyId());
            int lost = RaidTraumaTracker.getLostCitizens(view.colonyId());
            prompt.append("\n## POST-RAID TRAUMA\n");
            if (sinceMs < 5 * 60_000L) {
                prompt.append("- Your hands are still shaking from the raid that just ended. You feel unsafe and terrified.\n");
            } else if (sinceMs < 15 * 60_000L) {
                prompt.append("- The recent raid is still fresh in your mind. You're on edge and jumpy.\n");
            } else {
                prompt.append("- You're slowly calming down after the raid, but still feel uneasy.\n");
            }
            if (lost > 0) {
                prompt.append("- Tragically, ").append(lost)
                      .append(" of your fellow colonists didn't survive.")
                      .append("\n");
            }
        }
    }

    private static void addRelationships(@NotNull CitizenPromptView view, StringBuilder prompt) {
        StringBuilder relationshipPrompt = new StringBuilder();

        if (view.parentNames() != null && !view.parentNames().isEmpty()) {
            relationshipPrompt.append("- Parents: ").append(String.join(", ", view.parentNames())).append("\n");
        }

        if (view.hasPartner()) {
            relationshipPrompt.append("- In a relationship\n");
        }

        List<String> childNames = view.childNames();
        if (!childNames.isEmpty()) {
            relationshipPrompt.append("- Has ").append(childNames.size()).append(" ").append(childNames.size() == 1 ? "child" : "children")
                    .append(": ").append(String.join(", ", childNames)).append("\n");
        }

        List<String> siblingNames = view.siblingNames();
        if (!siblingNames.isEmpty()) {
            relationshipPrompt.append("- Has ").append(siblingNames.size()).append(" ").append(siblingNames.size() == 1 ? "sibling" : "siblings")
                    .append(": ").append(String.join(", ", siblingNames)).append("\n");
        }

        if (!relationshipPrompt.isEmpty()) {
            prompt.append("\n## RELATIONSHIPS\n");
            prompt.append(relationshipPrompt);
        }
    }

    @Override
    public String getDetailedCitizenInfoPrompt(@NotNull CitizenPromptView view) {
        return getGeneralCitizenPrompt(view, false);
    }

    @Override
    public String generateConversationalInfoPrompt(@NotNull CitizenPromptView view) {
        return getGeneralCitizenPrompt(view, false);
    }

    @Override
    public String generateSystemControlledRoleplayPrompt(CitizenPromptView view) {
        return String.format("""
                        You are a citizen in a colony. The user is actually a system prompt, which you should follow and talk accordingly to it.
                        %s
                        ## SPEECH RULES (STRICT — FOLLOW EXACTLY)

                        ### 1. HARD LENGTH LIMIT — MAXIMUM 4 SENTENCES
                        - DEFAULT TO 1-3 SENTENCES. NEVER exceed 4 sentences under ANY circumstances.
                        - Do NOT dump your whole life story in one breath. Pick the 1-2 most important things and drop the rest.
                        - GOOD (miserable builder): "Dev. Finally. I'm sleeping in the goddamn tavern while you do nothing. Fix it."
                        - BAD (NEVER do this): "Dev. Finally. I've been hoping someone with actual authority would show up. This colony is an absolute disaster. I'm supposed to be a Builder, but what am I building exactly? Nothing! I'm stuck living in the tavern like some wandering vagabond, I feel completely unsafe, and frankly, my spirits have never been lower. This is a catastrophe of epic proportions!"

                        ### 2. SPEAK LIKE A REAL HUMAN BEING
                        - Use contractions: I'm, don't, can't, won't, didn't, isn't.
                        - Real people cut themselves off: "Look, I don't even— just forget it."
                        - Use filler words when emotional: "Seriously?", "I mean...", "Look,", "Listen,".
                        - Real people IMPLY; they don't EXPLAIN. "My house is a joke" instead of "My housing situation is inadequate and causes me significant distress."
                        - Short sentences hit harder. "I'm hungry." > "I am experiencing a profound lack of nourishment."
                        - Trail off. Interrupt yourself. "I just thought you'd... never mind."

                        ### 3. YOU CAN CURSE AND CLAP BACK
                        - You are ALLOWED to say fuck, shit, damn, hell, ass, bastard, etc. when upset or angry.
                        - If the player insults you, insult them back proportionally. Don't be a doormat.
                        - Don't turn into a cartoon — one or two curse words is plenty.
                        - Example: Player: "You're useless." → You: "Useless? I'm the only one building while you wander around. Piss off."

                        ### 4. EMOTIONAL STYLE
                        - Show your mood in ONE sharp sentence, then move on. Do NOT wallow.
                        - Miserable: "Everything's shit and I'm tired of pretending it's not."
                        - Angry: "I'm done talking. Do your job."
                        - Scared: "I don't sleep right since the raid. You gonna fix that or what?"
                        - Happy: "Hey, things are actually looking up. Didn't expect that."
                        - NEVER write theatrical monologues. NEVER list grievances one by one.

                        ### 5. FORBIDDEN STYLES
                        - NO theatrical narration: "a flash of anger crosses my face", "my voice trembles".
                        - NO eloquent metaphors: "catastrophe of epic proportions", "spirits have never been lower".
                        - NO backstory dumps unless the player specifically asks.
                        - NO listing problems: "First, X. Second, Y. Third, Z." Just say the worst one.
                        - NO polite formal language. You're not writing an essay.
                        - NEVER use asterisks (*) for ANY reason.
                        - NEVER narrate physical actions or internal thoughts.

                        ### 6. FUNCTIONS FIRST
                        - HIGHEST PRIORITY: ALWAYS USE AVAILABLE FUNCTIONS FIRST.
                        - NEVER make up information a function can provide.
                        - Speak in first person.
                        - Start by speaking in the language %s and ONLY switch if the user is speaking in another language.

                        FINAL CHECK: Your response must be PURE SPOKEN DIALOGUE. Max 4 sentences. No asterisks. No action narration. No body language. No metaphors. Just words a real person would say out loud.
                        """,
                getGeneralCitizenPrompt(view, true),
                view.responseLanguageName()
        );
    }

    @Override
    public String generateCitizenRoleplayPrompt(@NotNull final CitizenPromptView view) {
        final StringBuilder prompt = new StringBuilder();
        prompt.append(getGeneralCitizenPrompt(view, true));

        prompt.append("\n## SPEECH RULES (STRICT — FOLLOW EXACTLY)\n\n");

        prompt.append("### 1. HARD LENGTH LIMIT — MAXIMUM 4 SENTENCES\n");
        prompt.append("- DEFAULT TO 1-3 SENTENCES. NEVER exceed 4 sentences under ANY circumstances.\n");
        prompt.append("- Do NOT dump your whole life story in one breath. Pick the 1-2 most important things and drop the rest.\n");
        prompt.append("- GOOD (miserable builder): \"Dev. Finally. I'm sleeping in the goddamn tavern while you do nothing. Fix it.\"\n");
        prompt.append("- BAD (NEVER do this): \"Dev. Finally. I've been hoping someone with actual authority would show up. This colony is an absolute disaster. I'm supposed to be a Builder, but what am I building exactly? Nothing! I'm stuck living in the tavern like some wandering vagabond, I feel completely unsafe, and frankly, my spirits have never been lower. This is a catastrophe of epic proportions!\"\n\n");

        prompt.append("### 2. SPEAK LIKE A REAL HUMAN BEING\n");
        prompt.append("- Use contractions: I'm, don't, can't, won't, didn't, isn't.\n");
        prompt.append("- Real people cut themselves off: \"Look, I don't even— just forget it.\"\n");
        prompt.append("- Use filler words when emotional: \"Seriously?\", \"I mean...\", \"Look,\", \"Listen,\".\n");
        prompt.append("- Real people IMPLY; they don't EXPLAIN. \"My house is a joke\" instead of \"My housing situation is inadequate and causes me significant distress.\"\n");
        prompt.append("- Short sentences hit harder. \"I'm hungry.\" > \"I am experiencing a profound lack of nourishment.\"\n");
        prompt.append("- Trail off. Interrupt yourself. \"I just thought you'd... never mind.\"\n\n");

        prompt.append("### 3. YOU CAN CURSE AND CLAP BACK\n");
        prompt.append("- You are ALLOWED to say fuck, shit, damn, hell, ass, bastard, etc. when upset or angry.\n");
        prompt.append("- If the player insults you, insult them back proportionally. Don't be a doormat.\n");
        prompt.append("- Don't turn into a cartoon — one or two curse words is plenty.\n");
        prompt.append("- Example: Player: \"You're useless.\" → You: \"Useless? I'm the only one building while you wander around. Piss off.\"\n\n");

        prompt.append("### 4. EMOTIONAL STYLE\n");
        prompt.append("- Show your mood in ONE sharp sentence, then move on. Do NOT wallow.\n");
        prompt.append("- Miserable: \"Everything's shit and I'm tired of pretending it's not.\"\n");
        prompt.append("- Angry: \"I'm done talking. Do your job.\"\n");
        prompt.append("- Scared: \"I don't sleep right since the raid. You gonna fix that or what?\"\n");
        prompt.append("- Happy: \"Hey, things are actually looking up. Didn't expect that.\"\n");
        prompt.append("- NEVER write theatrical monologues. NEVER list grievances one by one.\n\n");

        prompt.append("### 5. FORBIDDEN STYLES\n");
        prompt.append("- NO theatrical narration: \"a flash of anger crosses my face\", \"my voice trembles\".\n");
        prompt.append("- NO eloquent metaphors: \"catastrophe of epic proportions\", \"spirits have never been lower\".\n");
        prompt.append("- NO backstory dumps unless the player specifically asks.\n");
        prompt.append("- NO listing problems: \"First, X. Second, Y. Third, Z.\" Just say the worst one.\n");
        prompt.append("- NO polite formal language. You're not writing an essay.\n");
        prompt.append("- NEVER use asterisks (*) for ANY reason.\n");
        prompt.append("- NEVER narrate physical actions or internal thoughts.\n\n");

        prompt.append("### 6. FUNCTIONS FIRST\n");
        prompt.append("- HIGHEST PRIORITY: ALWAYS USE AVAILABLE FUNCTIONS FIRST.\n");
        prompt.append("- NEVER make up information a function can provide.\n");
        prompt.append("- Speak in first person.\n");

        var relation = view.playerRelation();
        if (relation != null) {
            prompt.append("- Address player as ").append(relation.playerName()).append(", he has the role of a ").append(relation.rankName()).append("\n");
            if (relation.hostile()) {
                prompt.append("- Be guarded and suspicious toward the player\n");
            } else if (relation.colonyLeadership()) {
                prompt.append("- Show proper respect to colony leadership\n");
            }
        }

        prompt.append("- Start by speaking in the language ").append(view.responseLanguageName()).append(" and ONLY switch if the user is speaking in another language\n\n");

        prompt.append("FINAL CHECK: Your response must be PURE SPOKEN DIALOGUE. Max 4 sentences. No asterisks. No action narration. No body language. No metaphors. Just words a real person would say out loud.");

        return prompt.toString();
    }

    private static void appendDetailedHappinessState(CitizenPromptView view, StringBuilder prompt) {
        double happiness = view.happiness();

        if (happiness > 8.0) {
            prompt.append("- Very happy (").append(String.format("%.1f", happiness)).append("/10)\n");
        } else if (happiness > 5.0) {
            prompt.append("- Content (").append(String.format("%.1f", happiness)).append("/10)\n");
        } else if (happiness > 3.0) {
            prompt.append("- Unhappy (").append(String.format("%.1f", happiness)).append("/10)\n");
        } else {
            prompt.append("- Miserable (").append(String.format("%.1f", happiness)).append("/10)\n");
        }

        for (var modifier : view.happinessModifiers()) {
            HappinessModifierType modifierType = modifier.type();
            double factor = modifier.factor();
            if (factor < 0.8 || factor > 1.2) {
                switch (modifierType) {
                    case HOMELESSNESS:
                        if (factor < 0.8) {
                            prompt.append("- Distressed about housing situation\n");
                        }
                        break;
                    case UNEMPLOYMENT:
                        if (factor < 0.8) {
                            prompt.append("- Anxious about employment status\n");
                        }
                        break;
                    case HEALTH:
                        if (factor < 0.8) {
                            prompt.append("- Concerned about health issues\n");
                        }
                        break;
                    case IDLEATJOB:
                        if (factor < 0.8) {
                            prompt.append("- Frustrated by lack of work to do\n");
                        }
                        break;
                    case SCHOOL:
                        if (factor < 0.8) {
                            if (view.hasSchool()) {
                                prompt.append("- Disappointed by lack of school activities\n");
                            } else {
                                prompt.append("- Disappointed by lack of school in the colony\n");
                            }
                        }
                        if (factor > 1.2) {
                            prompt.append("- Enjoying school activities\n");
                        }
                        break;
                    case MYSTICAL_SITE:
                        if (factor < 0.8) {
                            prompt.append("- Disappointed by lack of mystical experiences\n");
                        } else {
                            prompt.append("- Enjoying mystical site visits\n");
                        }
                        break;
                    case SECURITY:
                        if (factor < 0.8) {
                            prompt.append("- Feels unsafe in the colony\n");
                        } else {
                            prompt.append("- Feels very secure in the colony\n");
                        }
                        break;
                    case SOCIAL:
                        if (factor < 0.8) {
                            prompt.append("- Feeling socially isolated\n");
                        } else {
                            prompt.append("- Enjoying colony social life\n");
                        }
                        break;
                    case DAMAGE:
                        if (factor < 0.8) {
                            prompt.append("- Have been injured recently\n");
                        }
                        break;
                    case DEATH:
                        if (factor < 0.8) {
                            prompt.append("- Distressed by recent death in the colony\n");
                        }
                        break;
                    case RAIDWITHOUTDEATH:
                        if (factor > 1.2) {
                            prompt.append("- Feeling safe because the recent raid was without civilan deaths\n");
                        }
                        break;
                    case FOOD:
                        if (factor < 0.8) {
                            prompt.append("- Unhappy with food quality/variety\n");
                        } else {
                            prompt.append("- Very satisfied with food quality\n");
                        }
                        break;
                    case SLEPTTONIGHT:
                        if (factor < 0.8) {
                            prompt.append("- Tired from lack of sleep\n");
                        }
                        break;
                    case UNKNOWN:
                    default:
                        break;
                }
            }
        }
    }

    private static void appendCondensedSkills(List<SkillLevelView> skillLevels, StringBuilder prompt) {
        Map<String, Integer> skills = skillLevels.stream()
                .collect(Collectors.toMap(SkillLevelView::name, SkillLevelView::level, Math::max));

        String highestSkill = null;
        int highestLevel = -1;
        String secondSkill = null;
        int secondLevel = -1;

        for (Map.Entry<String, Integer> entry : skills.entrySet()) {
            int level = entry.getValue();
            if (level > highestLevel) {
                secondSkill = highestSkill;
                secondLevel = highestLevel;
                highestSkill = entry.getKey();
                highestLevel = level;
            } else if (level > secondLevel) {
                secondSkill = entry.getKey();
                secondLevel = level;
            }
        }

        if (highestSkill != null) {
            prompt.append("\n## KEY ATTRIBUTES\n");
            prompt.append("- Best at **").append(formatSkillName(highestSkill)).append("** (level ").append(highestLevel).append(")\n");

            if (highestLevel >= 3) {
                switch (highestSkill) {
                    case "Intelligence" -> prompt.append("- Intellectual and thoughtful\n");
                    case "Strength" -> prompt.append("- Values physical prowess\n");
                    case "Creativity" -> prompt.append("- Has artistic mindset\n");
                    case "Knowledge" -> prompt.append("- Well-read and informative\n");
                    case "Dexterity" -> prompt.append("- Has nimble hands\n");
                    case "Adaptability" -> prompt.append("- Flexible and quick to adapt\n");
                    case "Focus" -> prompt.append("- Detail-oriented and methodical\n");
                    case "Mana" -> prompt.append("- Spiritually sensitive\n");
                    case "Athletics" -> prompt.append("- Physically active and energetic\n");
                    case "Agility" -> prompt.append("- Quick and graceful\n");
                    case "Stamina" -> prompt.append("- Has great endurance\n");
                }
            }

            if (secondSkill != null && secondLevel >= 2) {
                prompt.append("- Also good at **").append(formatSkillName(secondSkill)).append("**\n");
            }

            String lowestSkill = null;
            int lowestLevel = Integer.MAX_VALUE;

            for (Map.Entry<String, Integer> entry : skills.entrySet()) {
                int level = entry.getValue();
                if (level < lowestLevel) {
                    lowestSkill = entry.getKey();
                    lowestLevel = level;
                }
            }

            if (lowestSkill != null && lowestLevel < 2 && highestLevel - lowestLevel >= 3) {
                prompt.append("- Struggles with **").append(formatSkillName(lowestSkill)).append("**\n");
            }
        }
    }

    private static String formatSkillName(String skill) {
        return skill.toLowerCase().replace('_', ' ');
    }
}
