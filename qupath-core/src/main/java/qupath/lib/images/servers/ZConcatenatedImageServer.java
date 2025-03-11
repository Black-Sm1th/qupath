package qupath.lib.images.servers;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class ZConcatenatedImageServer extends AbstractTileableImageServer {

    private final List<ImageServer<BufferedImage>> servers;
    private final ImageServerMetadata metadata;

    public ZConcatenatedImageServer(List<ImageServer<BufferedImage>> servers, Number zSpacingMicrons) {
        if (servers.isEmpty()) {
            throw new IllegalArgumentException("The provided list of image servers is empty");
        }

        List<ImageServerMetadata> metadata = servers.stream().map(ImageServer::getMetadata).toList();
        checkUniqueMetadata(metadata, ImageServerMetadata::getWidth, "width");
        checkUniqueMetadata(metadata, ImageServerMetadata::getHeight, "height");
        checkUniqueMetadata(metadata, ImageServerMetadata::getPixelCalibration, "pixel calibration");
        checkUniqueMetadata(metadata, ImageServerMetadata::isRGB, "RGB formats");
        checkUniqueMetadata(metadata, ImageServerMetadata::getPixelType, "pixel type");
        checkUniqueMetadata(metadata, ImageServerMetadata::getSizeT, "number of time points");
        checkUniqueMetadata(metadata, ImageServerMetadata::getChannels, "channels");
        checkUniqueMetadata(metadata, ImageServerMetadata::getChannelType, "channel type");
        checkUniqueMetadata(metadata, ImageServerMetadata::getClassificationLabels, "classification labels");
        checkUniqueMetadata(metadata, ImageServerMetadata::getMagnification, "magnification");

        if (metadata.stream().map(ImageServerMetadata::getSizeZ).anyMatch(sizeZ -> sizeZ != 1)) {
            throw new IllegalArgumentException(String.format(
                    "The number of z-stacks of one of the provided servers %s is not 1",
                    servers
            ));
        }

        this.servers = servers;
        this.metadata = new ImageServerMetadata.Builder(servers.getFirst().getMetadata())
                .name(String.format("Concatenation of %s", metadata.stream().map(ImageServerMetadata::getName).toList()))
                .zSpacingMicrons(zSpacingMicrons)
                .sizeZ(servers.size())
                .build();
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return new ImageServers.ZConcatenatedImageServerBuilder(
                getMetadata(),
                servers.stream().map(ImageServer::getBuilder).toList(),
                metadata.getZSpacingMicrons()
        );
    }

    @Override
    protected String createID() {
        return String.format("%s: %s", getClass().getName(), getURIs());
    }

    @Override
    protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
        ImageServer<BufferedImage> server = servers.get(tileRequest.getZ());

        return server.readRegion(tileRequest.getRegionRequest().updatePath(server.getPath()).updateZ(0));
    }

    @Override
    public Collection<URI> getURIs() {
        return servers.stream().map(ImageServer::getURIs).flatMap(Collection::stream).toList();
    }

    @Override
    public String getServerType() {
        return "Z-concatenated image server";
    }

    @Override
    public ImageServerMetadata getOriginalMetadata() {
        return metadata;
    }

    private static <T> void checkUniqueMetadata(
            List<ImageServerMetadata> metadata,
            Function<ImageServerMetadata, T> metadataGetter,
            String metadataLabel
    ) {
        List<T> distinctMetadata = metadata.stream().map(metadataGetter).distinct().toList();

        if (distinctMetadata.size() > 1) {
            throw new IllegalArgumentException(String.format(
                    "The provided image server %s %s are not the same",
                    metadataLabel,
                    distinctMetadata
            ));
        }
    }
}