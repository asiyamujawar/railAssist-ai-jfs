package com.trainconcierge.train;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainRepository extends JpaRepository<Train, Long> {

    Optional<Train> findByTrainNumber(String trainNumber);

    boolean existsByTrainNumber(String trainNumber);

    List<Train> findByOperatorName(String operatorName);

    List<Train> findByOriginStationAndDestinationStation(
            String originStation, String destinationStation);

    List<Train> findByActiveTrue();

    Page<Train> findAllByActiveTrue(Pageable pageable);

    Page<Train> findAll(Pageable pageable);

    @Query("""
        SELECT t FROM Train t
        WHERE t.active = true
          AND LOWER(t.originStation) = LOWER(:origin)
          AND LOWER(t.destinationStation) = LOWER(:destination)
        ORDER BY t.trainNumber ASC
    """)
    List<Train> findActiveByRouteIgnoreCase(
            @Param("origin") String origin,
            @Param("destination") String destination);

    @Query("""
        SELECT t FROM Train t
        WHERE LOWER(t.originStation) LIKE LOWER(CONCAT('%', :station, '%'))
           OR LOWER(t.destinationStation) LIKE LOWER(CONCAT('%', :station, '%'))
    """)
    List<Train> searchByStationContainingIgnoreCase(@Param("station") String station);

    @Query("SELECT COUNT(t) > 0 FROM Train t WHERE t.trainNumber = :trainNumber AND t.id <> :excludeId")
    boolean existsByTrainNumberAndIdNot(@Param("trainNumber") String trainNumber, @Param("excludeId") Long excludeId);
}
