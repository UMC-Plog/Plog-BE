package com.plog.domain.post.entity;

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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
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
@Table(name = "post_attachments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_post_attachment_file", columnNames = "file_id")
})
public class PostAttachment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_attachment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type")
    private AttachmentType attachmentType;

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

    @Column(name = "file_size")
    private Long fileSize;
}
