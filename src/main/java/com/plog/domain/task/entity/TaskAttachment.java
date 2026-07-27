package com.plog.domain.task.entity;

import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.plog.infrastructure.s3.UploadedFile;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "task_attachments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_task_attachment_file", columnNames = "file_id")
})
public class TaskAttachment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_attachment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type")
    private AttachmentType attachmentType;

    @Column(name = "file_size")
    private Long fileSize;

    /** FILE 전용. LINK 행은 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private UploadedFile uploadedFile;

    /** LINK 전용. 사용자가 입력한 외부 URL이라 수명 관리 대상이 아니고 중복을 허용한다. */
    @Column(name = "link_url", length = 512)
    private String linkUrl;

    /** LINK 표시명. FILE 은 uploadedFile.originalFilename 을 쓴다. */
    @Column(name = "link_name", length = 512)
    private String linkName;

    // 업무카드 생성 시점에 함께 등록되는 산출물 첨부. Task와 함께 저장된다.
    public static TaskAttachment create(Task task, AttachmentType attachmentType,
                                        Long fileSize, UploadedFile uploadedFile,
                                        String linkUrl, String linkName) {
        return TaskAttachment.builder()
                .task(task)
                .attachmentType(attachmentType)
                .fileSize(fileSize)
                .uploadedFile(uploadedFile)
                .linkUrl(linkUrl)
                .linkName(linkName)
                .build();
    }

    /** 표시용 파일명. FILE 은 레지스트리, LINK 는 사용자가 붙인 이름. */
    public String displayName() {
        return uploadedFile != null ? uploadedFile.getOriginalFilename() : linkName;
    }
}
