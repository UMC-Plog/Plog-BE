package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.LinkType;

public interface ExternalConnectionSummary {

    LinkType getLinkType();

    String getExternalAccountId();
}
