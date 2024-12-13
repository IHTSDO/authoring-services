package org.ihtsdo.authoringservices.domain;

public class TaskTransferRequest {

    User currentAssignee;
    User newAssignee;
    User currentLoggedUser;

    public TaskTransferRequest(User currentAssignee, User newAssignee, User currentLoggedUser) {
        this.currentAssignee = currentAssignee;
        this.newAssignee = newAssignee;
        this.currentLoggedUser = currentLoggedUser;
    }

    public User getCurrentAssignee() {
        return currentAssignee;
    }

    public User getNewAssignee() {
        return newAssignee;
    }

    public User getCurrentLoggedUser() {
        return currentLoggedUser;
    }

}