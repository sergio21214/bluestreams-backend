package com.spmf.observability;

import com.spmf.iptv.account.IptvAccount;
import com.spmf.iptv.account.IptvAccountStatus;
import com.spmf.music.MusicDownloadJob;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SystemStatsService {

    public record DbStats(
            SystemOverview.MusicStats music,
            SystemOverview.IptvStats iptv
    ) {
    }

    @Transactional
    public DbStats load() {

        SystemOverview.MusicStats music =
                new SystemOverview.MusicStats(
                        MusicDownloadJob.count(
                                "status",
                                MusicDownloadJob.Status.QUEUED
                        ),
                        MusicDownloadJob.count(
                                "status",
                                MusicDownloadJob.Status.DOWNLOADING
                        ),
                        MusicDownloadJob.count(
                                "status",
                                MusicDownloadJob.Status.PROCESSING
                        ),
                        MusicDownloadJob.count(
                                "status",
                                MusicDownloadJob.Status.COMPLETED
                        ),
                        MusicDownloadJob.count(
                                "status",
                                MusicDownloadJob.Status.FAILED
                        )
                );

        long active =
                IptvAccount.count(
                        "status",
                        IptvAccountStatus.ACTIVE
                );

        boolean defaultActive =
                IptvAccount.count(
                        "status = ?1 and isDefault = true",
                        IptvAccountStatus.ACTIVE
                ) > 0;

        SystemOverview.IptvStats iptv =
                new SystemOverview.IptvStats(
                        IptvAccount.count(),
                        active,
                        IptvAccount.count(
                                "status",
                                IptvAccountStatus.DEAD
                        ),
                        IptvAccount.count(
                                "status",
                                IptvAccountStatus.EXPIRED
                        ),
                        IptvAccount.count(
                                "status",
                                IptvAccountStatus.UNKNOWN
                        ),
                        defaultActive
                );

        return new DbStats(music, iptv);
    }
}
