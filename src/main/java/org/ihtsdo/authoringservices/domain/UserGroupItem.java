package org.ihtsdo.authoringservices.domain;

import java.util.List;

public class UserGroupItem {
    private List<JiraUser> items;

    private int size;

    public List<JiraUser> getItems() {
        return items;
    }

    public void setItems(List<JiraUser> items) {
        this.items = items;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
