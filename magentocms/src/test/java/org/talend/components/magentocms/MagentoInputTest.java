package org.talend.components.magentocms;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.talend.components.magentocms.common.*;
import org.talend.components.magentocms.input.SelectionFilter;
import org.talend.components.magentocms.input.SelectionFilterOperator;
import org.talend.components.magentocms.service.MagentoCmsService;
import org.talend.sdk.component.api.service.completion.SuggestionValues;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class MagentoInputTest {

    @Test
    public void testConnection() throws UnknownAuthenticationTypeException {
        AuthenticationOauth1Settings authenticationOauth1Settings = new AuthenticationOauth1Settings();
        AuthenticationTokenSettings authenticationTokenSettings = new AuthenticationTokenSettings();
        AuthenticationLoginPasswordSettings authenticationLoginPasswordSettings = new AuthenticationLoginPasswordSettings();
        MagentoCmsConfigurationBase magentoCmsConfigurationBase;
        magentoCmsConfigurationBase = new MagentoCmsConfigurationBase(null, null, AuthenticationType.OAUTH_1,
                authenticationOauth1Settings, authenticationTokenSettings, authenticationLoginPasswordSettings);
        assertEquals(authenticationOauth1Settings, magentoCmsConfigurationBase.getAuthSettings());
        magentoCmsConfigurationBase = new MagentoCmsConfigurationBase(null, null, AuthenticationType.AUTHENTICATION_TOKEN,
                authenticationOauth1Settings, authenticationTokenSettings, authenticationLoginPasswordSettings);
        assertEquals(authenticationTokenSettings, magentoCmsConfigurationBase.getAuthSettings());
        magentoCmsConfigurationBase = new MagentoCmsConfigurationBase(null, null, AuthenticationType.LOGIN_PASSWORD,
                authenticationOauth1Settings, authenticationTokenSettings, authenticationLoginPasswordSettings);
        assertEquals(authenticationLoginPasswordSettings, magentoCmsConfigurationBase.getAuthSettings());
    }

    @Test
    public void testAdvancedFilterSuggestion() throws UnsupportedEncodingException {
        List<SelectionFilter> filterLines = new ArrayList<>();
        SelectionFilter filter;
        filter = new SelectionFilter("sku", "eq", "24-MB01");
        filterLines.add(filter);
        filter = new SelectionFilter("sku", "like", "M%");
        filterLines.add(filter);
        SuggestionValues suggestionAnd = new MagentoCmsService().suggestFilterAdvanced(SelectionFilterOperator.AND, filterLines);
        String valAnd = suggestionAnd.getItems().iterator().next().getId();
        assertEquals("searchCriteria[filter_groups][0][filters][0][condition_type]=eq"
                + "&searchCriteria[filter_groups][0][filters][0][field]=sku"
                + "&searchCriteria[filter_groups][0][filters][0][value]=24-MB01"
                + "&searchCriteria[filter_groups][1][filters][0][condition_type]=like"
                + "&searchCriteria[filter_groups][1][filters][0][field]=sku"
                + "&searchCriteria[filter_groups][1][filters][0][value]=M%", valAnd);
        SuggestionValues suggestionOr = new MagentoCmsService().suggestFilterAdvanced(SelectionFilterOperator.OR, filterLines);
        String valOr = suggestionOr.getItems().iterator().next().getId();
        assertEquals("searchCriteria[filter_groups][0][filters][0][condition_type]=eq"
                + "&searchCriteria[filter_groups][0][filters][0][field]=sku"
                + "&searchCriteria[filter_groups][0][filters][0][value]=24-MB01"
                + "&searchCriteria[filter_groups][0][filters][1][condition_type]=like"
                + "&searchCriteria[filter_groups][0][filters][1][field]=sku"
                + "&searchCriteria[filter_groups][0][filters][1][value]=M%", valOr);

    }

}