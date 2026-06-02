package dev.vaniley.vanillapoints;

import dev.vaniley.vanillapoints.api.PointInfo;
import dev.vaniley.vanillapoints.api.PointMetadata;

final class PointInfoMapper {
    private PointInfoMapper() {
    }

    static PointInfo toInfo(StoredPoint point) {
        return new PointInfo(
                point.worldName(),
                point.x(),
                point.y(),
                point.z(),
                point.yaw(),
                point.pitch(),
                point.description(),
                point.icon(),
                point.createdBy(),
                point.createdAt()
        );
    }

    static StoredPoint applyMetadata(StoredPoint point, PointMetadata metadata) {
        if (metadata == null) {
            return point;
        }
        return point.withMetadata(metadata.description(), metadata.icon(), metadata.createdBy(), metadata.createdAt());
    }
}
