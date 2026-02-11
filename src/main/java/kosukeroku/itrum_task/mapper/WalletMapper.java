package kosukeroku.itrum_task.mapper;

import kosukeroku.itrum_task.dto.WalletResponseDTO;
import kosukeroku.itrum_task.model.Wallet;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WalletMapper {

    WalletResponseDTO toResponseDto(Wallet wallet);
}