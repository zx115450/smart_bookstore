package com.zx.reservation.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.reservation.entity.ReservationResource;
import com.zx.reservation.mapper.ReservationResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReservationResourceRepository {

    private final ReservationResourceMapper mapper;

    public List<ReservationResource> pageEnabled(long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        long offset = (safePage - 1) * safeSize;
        return mapper.selectList(
                Wrappers.<ReservationResource>lambdaQuery()
                        .eq(ReservationResource::getStatus, 1)
                        .orderByAsc(ReservationResource::getId)
                        .last("LIMIT " + safeSize + " OFFSET " + offset)
        );
    }

    public long countEnabled() {
        Long count = mapper.selectCount(
                Wrappers.<ReservationResource>lambdaQuery().eq(ReservationResource::getStatus, 1)
        );
        return count == null ? 0 : count;
    }

    public Optional<ReservationResource> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<ReservationResource> findEnabledById(Long id) {
        return findById(id).filter(r -> r.getStatus() != null && r.getStatus() == 1);
    }

    public ReservationResource save(ReservationResource resource) {
        LocalDateTime now = LocalDateTime.now();
        if (resource.getId() == null) {
            if (resource.getCreatedAt() == null) {
                resource.setCreatedAt(now);
            }
            resource.setUpdatedAt(now);
            mapper.insert(resource);
            return resource;
        }
        resource.setUpdatedAt(now);
        mapper.updateById(resource);
        return resource;
    }
}
