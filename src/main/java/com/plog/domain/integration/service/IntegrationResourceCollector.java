package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.LinkType;
import java.util.List;

interface IntegrationResourceCollector {

    List<LinkType> providers();

    void collect(IntegrationResource resource);
}