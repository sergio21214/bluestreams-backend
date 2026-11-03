package com.spmf.iptv.account;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class IptvAccountImportService {

    @Inject
    IptvAccountParser parser;

    @Inject
    IptvAccountStore store;

    public IptvAccountStore.ImportResult importText(String text) {
        return store.importParsed(parser.parse(text));
    }
}
