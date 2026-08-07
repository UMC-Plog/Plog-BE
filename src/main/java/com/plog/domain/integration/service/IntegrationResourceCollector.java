package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import java.util.List;

interface IntegrationResourceCollector {

    List<LinkType> providers();

    void collect(
            IntegrationResource resource,
            ProjectIntegration verifiedIntegration,
            CollectionContext context
    );
}
