package com.rfizzle.mercantile.follow;

public interface FollowableVillager {
    void mercantile$setFollowingSync(boolean following);

    boolean mercantile$isFollowingSync();

    void mercantile$setReturningHomeSync(boolean returning);

    boolean mercantile$isReturningHomeSync();
}
