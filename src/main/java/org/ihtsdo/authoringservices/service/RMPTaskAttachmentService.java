package org.ihtsdo.authoringservices.service;

import jakarta.transaction.Transactional;
import org.ihtsdo.authoringservices.entity.RMPTask;
import org.ihtsdo.authoringservices.entity.RMPTaskAttachment;
import org.ihtsdo.authoringservices.repository.RMPTaskAttachmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class RMPTaskAttachmentService {

    private final RMPTaskAttachmentRepository attachmentRepository;

    @Autowired
    public RMPTaskAttachmentService(RMPTaskAttachmentRepository attachmentRepository) {
        this.attachmentRepository = attachmentRepository;
    }

    public List<RMPTaskAttachment> findByRmpTask(RMPTask rmpTask) {
        return attachmentRepository.findByRmpTaskOrderByCreatedDateAsc(rmpTask);
    }

    public Optional<RMPTaskAttachment> getByTaskAndId(long taskId, long attachmentId) {
        return attachmentRepository.findByIdAndRmpTask_Id(attachmentId, taskId);
    }

    public RMPTaskAttachment saveAttachment(RMPTask rmpTask, MultipartFile file, String username) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        RMPTaskAttachment attachment = new RMPTaskAttachment();
        attachment.setRmpTask(rmpTask);
        attachment.setUser(username);
        String original = file.getOriginalFilename();
        attachment.setFileName(StringUtils.hasLength(original) ? original : "attachment");
        attachment.setContentType(file.getContentType());
        attachment.setContentSize(file.getSize());
        attachment.setContent(file.getBytes());
        return attachmentRepository.save(attachment);
    }

    @Transactional
    public boolean deleteAttachment(long taskId, long attachmentId) {
        Optional<RMPTaskAttachment> attachment = attachmentRepository.findByIdAndRmpTask_Id(attachmentId, taskId);
        if (attachment.isPresent()) {
            attachmentRepository.delete(attachment.get());
            return true;
        }
        return false;
    }

}
