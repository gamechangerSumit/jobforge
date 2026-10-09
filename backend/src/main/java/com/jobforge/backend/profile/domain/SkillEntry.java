package com.jobforge.backend.profile.domain;

import java.math.BigDecimal;

public record SkillEntry(String skill, SkillProficiency proficiency, BigDecimal years) {}
