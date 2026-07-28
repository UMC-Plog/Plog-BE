package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.LinkType;

interface IntegrationResourceCollector {

    LinkType provider();

    void collect(IntegrationResource resource);
}
