package org.ihtsdo.authoringservices.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class LineItem {
    private String id;

    private String subjectId;

    private String parentId;

    private Integer level;

    private String content;

    private Integer sequence;

    private String sourceBranch;

    private String promotedBranch;

    private String start;

    private String end;

    private boolean released;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getParentId() {
        return parentId;
    }


    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public void setSourceBranch(String sourceBranch) {
        this.sourceBranch = sourceBranch;
    }

    public String getPromotedBranch() {
        return promotedBranch;
    }

    public void setPromotedBranch(String promotedBranch) {
        this.promotedBranch = promotedBranch;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public boolean getReleased() {
        return released;
    }

    public void setReleased(boolean released) {
        this.released = released;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LineItem lineItem = (LineItem) o;

        if (id != null || lineItem.id != null) {
            return Objects.equals(id, lineItem.id);
        }

        return Objects.equals(subjectId, lineItem.subjectId)
                && Objects.equals(content, lineItem.content)
                && Objects.equals(sourceBranch, lineItem.sourceBranch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, subjectId);
    }

    @Override
    public String toString() {
        return "LineItem{" +
                "id='" + id + '\'' +
                ", subjectId='" + subjectId + '\'' +
                ", parentId='" + parentId + '\'' +
                ", level=" + level +
                ", content='" + content + '\'' +
                ", sequence=" + sequence +
                ", sourceBranch='" + sourceBranch + '\'' +
                ", promotedBranch='" + promotedBranch + '\'' +
                ", start=" + start +
                ", end=" + end +
                ", released=" + released +
                '}';
    }
}
