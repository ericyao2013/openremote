/*
 * Copyright 2017, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.manager.client.rules;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import org.openremote.manager.client.assets.browser.AssetBrowser;
import org.openremote.manager.client.i18n.ManagerMessages;
import org.openremote.manager.client.style.FormTreeStyle;
import org.openremote.manager.client.widget.FlexSplitPanel;

import javax.inject.Inject;
import java.util.logging.Logger;

public class RulesGlobalViewImpl extends Composite implements RulesGlobalView {

    private static final Logger LOG = Logger.getLogger(RulesGlobalViewImpl.class.getName());

    interface UI extends UiBinder<FlexSplitPanel, RulesGlobalViewImpl> {
    }

    @UiField
    ManagerMessages managerMessages;

    @UiField
    HTMLPanel sidebarContainer;

    @UiField
    SimplePanel rulesContentContainer;

    final AssetBrowser assetBrowser;
    final FormTreeStyle formTreeStyle;

    Presenter presenter;

    @Inject
    public RulesGlobalViewImpl(AssetBrowser assetBrowser, FormTreeStyle formTreeStyle) {
        this.assetBrowser = assetBrowser;
        this.formTreeStyle = formTreeStyle;

        RulesGlobalViewImpl.UI ui = GWT.create(RulesGlobalViewImpl.UI.class);
        initWidget(ui.createAndBindUi(this));
    }

    @Override
    public void setPresenter(Presenter presenter) {
        this.presenter = presenter;
        if (presenter != null) {
            assetBrowser.asWidget().removeFromParent();
            sidebarContainer.add(assetBrowser.asWidget());
        } else {
            sidebarContainer.clear();
        }
    }
}
