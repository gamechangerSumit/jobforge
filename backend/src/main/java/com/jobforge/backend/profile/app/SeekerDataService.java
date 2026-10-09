package com.jobforge.backend.profile.app;

import com.jobforge.backend.profile.domain.SeekerProfile;
import com.jobforge.backend.profile.facade.SeekerDataFacade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeekerDataService implements SeekerDataFacade {

    private final SeekerProfileRepository profiles;
    private final ResumeLookup resumes;

    public SeekerDataService(SeekerProfileRepository profiles, ResumeLookup resumes) {
        this.profiles = profiles;
        this.resumes = resumes;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfileSnapshot> snapshot(UUID seekerUserId) {
        return profiles.findByUserId(seekerUserId).map(p -> new ProfileSnapshot(
                p.headline(), p.skills().stream().map(s -> s.skill()).toList(), p.yearsExperience()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean ownsActiveResume(UUID seekerUserId, UUID resumeId) {
        return resumes.ownsActiveResume(seekerUserId, resumeId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CandidateProfile> candidate(UUID seekerUserId) {
        return profiles.findByUserId(seekerUserId).map(SeekerDataService::toCandidate);
    }

    private static CandidateProfile toCandidate(SeekerProfile p) {
        LocationView location = p.location() == null ? null
                : new LocationView(p.location().city(), p.location().state(), p.location().country());
        LinksView links = p.links() == null ? null
                : new LinksView(p.links().linkedin(), p.links().github(), p.links().portfolio());
        return new CandidateProfile(p.id(), p.userId(), p.headline(), p.summary(), location, p.currentTitle(),
                p.yearsExperience(), p.noticePeriodDays(), p.openToWork(), p.visibility().name(), links,
                p.completenessScore(),
                p.skills().stream().map(s -> new SkillView(s.skill(), s.proficiency().name(), s.years())).toList());
    }
}
