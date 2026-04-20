package org.ihtsdo.authoringservices.service;

import jakarta.transaction.Transactional;
import org.ihtsdo.authoringservices.entity.RMPTask;
import org.ihtsdo.authoringservices.entity.RMPTaskAttachment;
import org.ihtsdo.authoringservices.repository.RMPTaskAttachmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RMPTaskAttachmentService {

    private final RMPTaskAttachmentRepository attachmentRepository;
    private final Set<String> allowedExtensions;

    @Autowired
    public RMPTaskAttachmentService(
            RMPTaskAttachmentRepository attachmentRepository,
            @Value("${rmp.task.attachments.allowed-extensions}") String allowedExtensionsConfig) {
        this.attachmentRepository = attachmentRepository;
        this.allowedExtensions = parseAllowedExtensions(allowedExtensionsConfig);
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
        String original = file.getOriginalFilename();
        if (!StringUtils.hasLength(original) || !hasAllowedExtension(original)) {
            throw new IllegalArgumentException("Only these file extensions are allowed: " + String.join(", ", allowedExtensions));
        }
        RMPTaskAttachment attachment = new RMPTaskAttachment();
        attachment.setRmpTask(rmpTask);
        attachment.setUser(username);
        attachment.setFileName(original);
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

    private Set<String> parseAllowedExtensions(String allowedExtensionsConfig) {
        Set<String> parsed = Arrays.stream(allowedExtensionsConfig.split(","))
                .map(String::trim)
                .filter(StringUtils::hasLength)
                .map(this::normalizeExtension)
                .filter(StringUtils::hasLength)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (parsed.isEmpty()) {
            throw new IllegalStateException("Property rmp.task.attachments.allowed-extensions must define at least one extension");
        }
        return parsed;
    }

    private boolean hasAllowedExtension(String fileName) {
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == fileName.length() - 1) {
            return false;
        }
        String extension = normalizeExtension(fileName.substring(extensionStart + 1));
        return allowedExtensions.contains(extension);
    }

    private String normalizeExtension(String extension) {
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            return normalized.substring(1);
        }
        return normalized;
    }

}
