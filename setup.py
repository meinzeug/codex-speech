from setuptools import setup, find_packages

setup(
    name='codex-stt-assistant',
    version='0.1.0',
    description='Speech-to-Text enabled GUI for Codex CLI',
    author='Your Name',
    author_email='your.email@example.com',
    url='https://github.com/yourusername/codex-stt-assistant',
    packages=find_packages(where='src'),
    package_dir={'': 'src'},
    include_package_data=True,
    package_data={
        'codex_stt_assistant': [
            'resources/**/*',
        ]
    },
    install_requires=[
        'PyGObject>=3.42.0',
        'pycairo>=1.20.0',
        'pyaudio>=0.2.13',
        'vosk>=0.3.45',
        'webrtcvad>=2.0.10',
        'requests>=2.28.0',
        'aiohttp>=3.8.0',
        'pyte>=0.8.0',
    ],
    extras_require={
        'dev': [
            'pytest>=7.0.0',
            'pytest-cov>=3.0.0',
            'black>=22.0.0',
            'flake8>=4.0.0',
            'mypy>=0.950',
        ]
    },
    entry_points={
        'console_scripts': [
            'codex-stt-assistant=codex_stt_assistant.main:main',
        ],
    },
    classifiers=[
        'Development Status :: 4 - Beta',
        'Intended Audience :: Developers',
        'License :: OSI Approved :: MIT License',
        'Programming Language :: Python :: 3',
        'Programming Language :: Python :: 3.9',
        'Programming Language :: Python :: 3.10',
        'Programming Language :: Python :: 3.11',
        'Operating System :: POSIX :: Linux',
        'Topic :: Software Development',
        'Topic :: Utilities',
    ],
    python_requires='>=3.9',
)
