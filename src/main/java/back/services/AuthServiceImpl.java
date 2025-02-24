package back.services;

import back.dto.LoginRequest;
import back.dto.LoginResponse;
import back.entities.Person;
import back.repositories.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

  private final PersonRepository personRepository;
  private final ModelMapper modelMapper;

  @Override
  @Transactional
  public LoginResponse login(LoginRequest loginRequest) {
    Person person = personRepository.findByEmail(loginRequest.getEmail());
    if (person != null) {
      throw new IllegalStateException("Пользователь с таким email уже существует.");
    }
    person = modelMapper.map(loginRequest, Person.class);
    personRepository.save(person);
    return new LoginResponse(person.getId() ,person.getEmail());
  }

}
