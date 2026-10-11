package com.jobforge.backend.user.app;

import com.jobforge.backend.user.facade.UserSummaryFacade.UserSummaryView;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UserSummaryRepository {

    List<UserSummaryView> summaries(List<UUID> ids);

    Map<String, UUID> activeIdsByHandle(List<String> lowerCaseHandles);
}
