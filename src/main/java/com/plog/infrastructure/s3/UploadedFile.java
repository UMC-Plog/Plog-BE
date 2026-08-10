package com.plog.infrastructure.s3;

import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

/**
 * 업로드된 S3 객체의 레지스트리. 도메인 첨부 테이블은 이 행을 FK로 참조한다.
 * <p>
 * file_key 를 도메인마다 문자열로 들고 있으면 "이 객체를 누가 참조하는가"에 대한
 * 단일 진실 공급원이 없어, 한쪽을 지우면서 다른 쪽이 쓰는 객체를 삭제하게 된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "uploaded_file", indexes = {
        @Index(name = "idx_uploaded_file_tagging", columnList = "status, tagged_at"),
        @Index(name = "idx_uploaded_file_issued", columnList = "status, issued_at"),
        @Index(name = "idx_uploaded_file_released", columnList = "status, released_at"),
        @Index(name = "idx_uploaded_file_thumbnail", columnList = "thumbnail_status, thumbnail_at")
})
public class UploadedFile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "uploaded_file_id")
    private Long id;

    /** 전역 UNIQUE. 같은 S3 키로 레지스트리 행이 둘 생기는 것을 막는다. */
    @Column(name = "file_key", nullable = false, unique = true, length = 512)
    private String fileKey;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 16)
    private AttachmentUsage purpose;

    @Column(name = "original_filename", nullable = false, length = 512)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size")
    private Long size;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UploadedFileStatus status;

    /**
     * presigned URL 발급 시각. BaseEntity.createdAt 과 값이 같지만 분리해 둔다 —
     * createdAt 은 JPA 감사 메타데이터고, issued_at 은 스케줄러가 조건으로 쓰는
     * 업무 상태다. 감사 설정을 바꿨다고 회수 잡의 동작이 달라지면 안 된다.
     */
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    /** 현재 status 를 S3 태그에 반영 완료한 시각. null 이면 스케줄러의 작업 대상. */
    @Column(name = "tagged_at")
    private LocalDateTime taggedAt;

    /**
     * 썸네일 생성 상태. 필드 초기화는 issue() 가 이 값을 안 건드리기 때문이다 —
     * 채팅 이미지인지는 첨부가 확정될 때(confirmNew) 판정한다.
     * <p>
     * columnDefinition 으로 DB 기본값을 <b>반드시</b> 함께 준다. 필드 초기화는 DDL 에
     * 실리지 않으므로, 이게 없으면 Hibernate 가 만드는 스키마에는 DEFAULT 가 없어
     * JPA 를 거치지 않는 INSERT(테스트 픽스처의 raw SQL, 수동 데이터 이관)가 NOT NULL
     * 위반으로 깨진다. 운영은 수동 DDL 로 DEFAULT 를 넣었으므로 스키마가 갈리는 것도 막는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "thumbnail_status", nullable = false, length = 16,
            columnDefinition = "varchar(16)")
    @ColumnDefault("'NONE'")
    private ThumbnailStatus thumbnailStatus = ThumbnailStatus.NONE;

    /**
     * 키는 원본에서 결정론적으로 유도되지만 저장한다. 규칙을 나중에 바꿔도 이미 만들어진
     * 썸네일이 안 깨지고, 조회할 때마다 규칙을 재계산하지 않는다.
     */
    @Column(name = "thumbnail_key", length = 512)
    private String thumbnailKey;

    /** 마지막 Lambda Invoke 시각. null 이면 아직 요청 전 = 스케줄러의 안전망 대상. */
    @Column(name = "thumbnail_at")
    private LocalDateTime thumbnailAt;

    /** 운영 DDL 과 타입·기본값을 맞춘다(thumbnailStatus 의 columnDefinition 주석 참조). */
    @Column(name = "thumbnail_attempts", nullable = false,
            columnDefinition = "smallint")
    @ColumnDefault("0")
    private int thumbnailAttempts;

    public static UploadedFile issue(String fileKey, Long ownerId, AttachmentUsage purpose,
                                     String originalFilename, String contentType, Long size,
                                     LocalDateTime issuedAt) {
        UploadedFile file = new UploadedFile();
        file.fileKey = fileKey;
        file.ownerId = ownerId;
        file.purpose = purpose;
        file.originalFilename = originalFilename;
        file.contentType = contentType;
        file.size = size;
        file.status = UploadedFileStatus.PENDING;
        file.issuedAt = issuedAt;
        // state=pending 태그는 백엔드가 아니라 클라이언트의 PUT 이 x-amz-tagging 으로
        // 싣는다. null 로 두면 태깅 잡이 아직 없는 객체에 putObjectTagging 을 날린다.
        file.taggedAt = issuedAt;
        return file;
    }

    /**
     * 조건부 UPDATE 성공 후 메모리 상태를 DB 와 맞춘다. 값이 같으므로 더티 체킹이
     * 다시 써도 무해하다. 이걸 빼면 같은 트랜잭션에서 이 인스턴스가 플러시될 때
     * status=PENDING, confirmed_at=null 이 되돌아 써진다.
     */
    public void confirm(LocalDateTime confirmedAt) {
        this.status = UploadedFileStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
        this.taggedAt = null;
    }

    public void release(LocalDateTime releasedAt) {
        this.status = UploadedFileStatus.ORPHANED;
        this.releasedAt = releasedAt;
        this.taggedAt = null;
    }

    public void markTagged(LocalDateTime taggedAt) {
        this.taggedAt = taggedAt;
    }

    /**
     * 썸네일 생성 대상으로 표시한다. 실제 Invoke 는 커밋 후에 일어난다.
     * <p>
     * thumbnailAt 을 반드시 비운다. 남겨 두면 ThumbnailScheduler.requestPending() 의
     * "at IS NULL" 조건에 안 걸려, Invoke 가 유실됐을 때 아무도 재요청하지 않는다.
     */
    public void markThumbnailPending() {
        this.thumbnailStatus = ThumbnailStatus.PENDING;
        this.thumbnailAt = null;
    }

    public boolean isThumbnailPending() {
        return thumbnailStatus == ThumbnailStatus.PENDING;
    }

    public boolean isThumbnailReady() {
        return thumbnailStatus == ThumbnailStatus.READY;
    }
}
