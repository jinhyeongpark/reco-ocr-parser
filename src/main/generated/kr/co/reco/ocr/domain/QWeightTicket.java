package kr.co.reco.ocr.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QWeightTicket is a Querydsl query type for WeightTicket
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QWeightTicket extends EntityPathBase<WeightTicket> {

    private static final long serialVersionUID = -1885532534L;

    public static final QWeightTicket weightTicket = new QWeightTicket("weightTicket");

    public final StringPath carNumber = createString("carNumber");

    public final NumberPath<Double> confidence = createNumber("confidence", Double.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Double> grossWeight = createNumber("grossWeight", Double.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final BooleanPath needsReview = createBoolean("needsReview");

    public final NumberPath<Double> netWeight = createNumber("netWeight", Double.class);

    public final StringPath reviewNote = createString("reviewNote");

    public final DateTimePath<java.time.LocalDateTime> scaledAt = createDateTime("scaledAt", java.time.LocalDateTime.class);

    public final NumberPath<Double> tareWeight = createNumber("tareWeight", Double.class);

    public QWeightTicket(String variable) {
        super(WeightTicket.class, forVariable(variable));
    }

    public QWeightTicket(Path<? extends WeightTicket> path) {
        super(path.getType(), path.getMetadata());
    }

    public QWeightTicket(PathMetadata metadata) {
        super(WeightTicket.class, metadata);
    }

}

