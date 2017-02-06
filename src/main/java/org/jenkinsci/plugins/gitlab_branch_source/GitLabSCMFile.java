package org.jenkinsci.plugins.gitlab_branch_source;

import com.fasterxml.jackson.databind.JsonMappingException;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFile.Type;
import org.gitlab.api.models.GitlabProject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

class GitLabSCMFile extends SCMFile {

    private TypeInfo info;
    private final GitlabProject repo;
    private final String ref;
    private transient Object metadata;
    private transient boolean resolved;

    GitLabSCMFile(GitlabProject repo, String ref) {
        super();
        type(Type.DIRECTORY);
        info = TypeInfo.DIRECTORY_ASSUMED; // we have not resolved the metadata yet
        this.repo = repo;
        this.ref = ref;
    }

    private GitLabSCMFile(@NonNull GitLabSCMFile parent, String name, TypeInfo info) {
        super(parent, name);
        this.info = info;
        this.repo = parent.repo;
        this.ref = parent.ref;
    }

    private GitLabSCMFile(@NonNull GitLabSCMFile parent, String name, GHContent metadata) {
        super(parent, name);
        this.repo = parent.repo;
        this.ref = parent.ref;
        if (metadata.isDirectory()) {
            info = TypeInfo.DIRECTORY_CONFIRMED;
            // we have not listed the children yet, but we know it is a directory
        } else {
            info = TypeInfo.NON_DIRECTORY_CONFIRMED;
            this.metadata = metadata;
            resolved = true;
        }
    }

    private Object metadata() throws IOException {
        if (metadata == null && !resolved) {
            try {
                switch (info) {
                    case DIRECTORY_ASSUMED:
                        metadata = repo.getDirectoryContent(getPath(), ref);
                        info = TypeInfo.DIRECTORY_CONFIRMED;
                        resolved = true;
                        break;
                    case DIRECTORY_CONFIRMED:
                        metadata = repo.getDirectoryContent(getPath(), ref);
                        resolved = true;
                        break;
                    case NON_DIRECTORY_CONFIRMED:
                        metadata = repo.getFileContent(getPath(), ref);
                        resolved = true;
                        break;
                    case UNRESOLVED:
                        try {
                            metadata = repo.getFileContent(getPath(), ref);
                            info = TypeInfo.NON_DIRECTORY_CONFIRMED;
                            resolved = true;
                        } catch (IOException e) {
                            if (e.getCause() instanceof IOException
                                    && e.getCause().getCause() instanceof JsonMappingException) {
                                metadata = repo.getDirectoryContent(getPath(), ref);
                                info = TypeInfo.DIRECTORY_CONFIRMED;
                                resolved = true;
                            } else {
                                throw e;
                            }
                        }
                        break;
                }
            } catch (FileNotFoundException e) {
                metadata = null;
                resolved = true;
            }
        }
        return metadata;
    }

    @NonNull
    @Override
    protected SCMFile newChild(String name, boolean assumeIsDirectory) {
        return new GitLabSCMFile(this, name, assumeIsDirectory ? TypeInfo.DIRECTORY_ASSUMED: TypeInfo.UNRESOLVED);
    }

    @NonNull
    @Override
    public Iterable<SCMFile> children() throws IOException {
        List<GHContent> content = repo.getDirectoryContent(getPath(), ref);
        List<SCMFile> result = new ArrayList<>(content.size());
        for (GHContent c : content) {
            result.add(new GitLabSCMFile(this, c.getName(), c));
        }
        return result;
    }

    @Override
    public long lastModified() throws IOException, InterruptedException {
        // TODO see if we can find a way to implement it
        return 0L;
    }

    @NonNull
    @Override
    protected Type type() throws IOException, InterruptedException {
        Object metadata = metadata();
        if (metadata instanceof List) {
            return Type.DIRECTORY;
        }
        if (metadata instanceof GHContent) {
            GHContent content = (GHContent) metadata;
            if ("symlink".equals(content.getType())) {
                return Type.LINK;
            }
            if (content.isFile()) {
                return Type.REGULAR_FILE;
            }
            return Type.OTHER;
        }
        return Type.NONEXISTENT;
    }

    @NonNull
    @Override
    public InputStream content() throws IOException, InterruptedException {
        Object metadata = metadata();
        if (metadata instanceof List) {
            throw new IOException("Directory");
        }
        if (metadata instanceof GHContent) {
            return ((GHContent)metadata).read();
        }
        throw new FileNotFoundException(getPath());
    }

    private enum TypeInfo {
        UNRESOLVED,
        DIRECTORY_ASSUMED,
        DIRECTORY_CONFIRMED,
        NON_DIRECTORY_CONFIRMED;
    }

}