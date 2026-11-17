package dev.ihaletakip.web;

import dev.ihaletakip.servis.Hatalar.IsKuraliIhlali;
import dev.ihaletakip.servis.Hatalar.KayitBulunamadi;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class HataYoneticisi {

    @ExceptionHandler(IsKuraliIhlali.class)
    public ProblemDetail isKurali(IsKuraliIhlali e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(KayitBulunamadi.class)
    public ProblemDetail yok(KayitBulunamadi e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail yetki(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz yok");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail kimlik(BadCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail cakisma(ObjectOptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Kayıt başka bir kullanıcı tarafından değiştirildi; sayfayı yenileyip tekrar deneyin");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail butunluk(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Veri bütünlüğü kuralı ihlal edildi");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail dogrulama(MethodArgumentNotValidException e) {
        String ayrinti = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ayrinti);
    }
}
