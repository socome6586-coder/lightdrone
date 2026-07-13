package com.lightdrone.domain;

import com.lightdrone.support.YoutubeUtils;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "support_videos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportVideo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "support_video_seq")
    @SequenceGenerator(name = "support_video_seq", sequenceName = "support_video_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "youtube_url", nullable = false, length = 500)
    private String youtubeUrl;

    @Column(length = 50)
    private String category;

    @Column(nullable = false)
    @Builder.Default
    private boolean visible = true;

    @Column(nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Transient
    public String getThumbnailUrl() {
        return YoutubeUtils.thumbnailUrl(youtubeUrl);
    }

    @Transient
    public String getEmbedUrl() {
        return YoutubeUtils.embedUrl(youtubeUrl);
    }
}
