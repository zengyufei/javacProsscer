package control;

import entity.B;
import entity.D;
import lombok.*;
import org.springframework.validation.annotation.Controller;


@Controller
@Insert(B.class)
@Update(dto = B.VO.class)
public class Cu extends C<B> {

}
