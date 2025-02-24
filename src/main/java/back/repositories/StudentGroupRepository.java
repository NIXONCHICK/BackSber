package back.repositories;

import back.entities.StudentGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StudentGroupRepository extends JpaRepository<StudentGroup, Long> {
  Optional<StudentGroup> findByName(String name);
}
